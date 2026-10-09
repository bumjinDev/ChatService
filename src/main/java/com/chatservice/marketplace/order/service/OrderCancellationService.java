package com.chatservice.marketplace.order.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.chatservice.marketplace.common.error.BusinessException;
import com.chatservice.marketplace.common.error.ErrorCode;
import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.conversation.service.IConversationService;
import com.chatservice.marketplace.order.PurchaseOrderRepository;
import com.chatservice.marketplace.order.ShipmentRepository;
import com.chatservice.marketplace.order.domain.OrderEnums.CancelledBy;
import com.chatservice.marketplace.order.domain.OrderEnums.ShippingStatus;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.PurchaseOrder;
import com.chatservice.marketplace.wallet.domain.BalanceTransactionType;
import com.chatservice.marketplace.wallet.service.IWalletService;

@Service
public class OrderCancellationService implements IOrderCancellationService {

    /** 자동 취소의 취소 사유 고정값(설계 4.2 CANCEL_REASON). */
    public static final String AUTO_CANCEL_REASON = "SHIPMENT_DEADLINE_EXPIRED";

    private static final Logger logger = LoggerFactory.getLogger(OrderCancellationService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ShipmentRepository shipmentRepository;
    private final IWalletService walletService;
    private final IConversationService conversationService;
    private final TransactionTemplate transactionTemplate;
    private final TimeRules timeRules;

    public OrderCancellationService(PurchaseOrderRepository purchaseOrderRepository,
                                    ShipmentRepository shipmentRepository, IWalletService walletService,
                                    IConversationService conversationService, TransactionTemplate transactionTemplate,
                                    TimeRules timeRules) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.shipmentRepository = shipmentRepository;
        this.walletService = walletService;
        this.conversationService = conversationService;
        this.transactionTemplate = transactionTemplate;
        this.timeRules = timeRules;
    }

    /*
     * 처리 순서(설계 6.12): 주문(404) → 당사자(403) → 최종 상태면 409 TRADE_ALREADY_FINALIZED →
     * 발송 대기가 아니면 409 ORDER_ALREADY_SHIPPED(보류 주문은 배송 완료 뒤라 여기서 거절) → 공통 취소 처리.
     * 이미 취소한 주문을 다시 취소하면 상태 검사에서 거절되므로 순차 요청에서는 반환이 반복되지 않는다.
     */
    @Override
    @Transactional
    public void cancelByParty(String memberId, Long orderId, String reason) {
        // 주문을 조회한다. 주문이 없으면 404로 거절한다.
        PurchaseOrder order = purchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        // 요청한 회원이 이 주문의 구매자나 판매자가 아니면 403으로 거절한다.
        if (!order.isParty(memberId)) {
            throw new BusinessException(ErrorCode.NOT_TRADE_PARTY);
        }
        // 거래가 이미 최종 상태이면 409로 거절한다.
        if (order.isFinalized()) {
            // 호출 흐름: OrderCancellationService.cancelByParty() → PurchaseOrder.isFinalized() → TradeStatus.isFinal()
            throw new BusinessException(ErrorCode.TRADE_ALREADY_FINALIZED);
            /*
             * 최종 상태로 판단하는 TRADE_STATUS 값
             * - COMPLETED: 정상 완료
             * - REFUNDED: 환불 완료
             * - CANCELLED: 취소 완료
             * IN_PROGRESS(진행 중)와 ON_HOLD(보류)는 이 검사를 통과한다.
             */
        }
        // 배송 상태가 발송 대기(WAITING_SHIPMENT)가 아니면 409로 거절한다.
        // ON_HOLD 주문은 배송 완료(DELIVERED) 상태이므로 여기서 거절된다.
        if (order.getShippingStatus() != ShippingStatus.WAITING_SHIPMENT) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_SHIPPED);
            /*
             * 거절하는 SHIPPING_STATUS 값
             * - SHIPPING: 배송 중
             * - DELIVERED: 배송 완료
             * WAITING_SHIPMENT(발송 대기)만 이 검사를 통과한다.
             */
        }
        // 주문 취소 : 요청자가 구매자이면 BUYER, 판매자이면 SELLER를 취소 주체로 넘겨 공통 취소 처리를 실행한다. - 요구사항 명세서에 구매자 판매자 모두 가능 명시됨
        cancel(order, order.isBuyer(memberId) ? CancelledBy.BUYER : CancelledBy.SELLER, reason, timeRules.now());
    }

    /**
     * 발송 기한이 지나도록 발송 정보가 등록되지 않은 주문을 찾아 자동 취소한다.
     *
     * 처리 흐름:
     *   1. 진행 중(IN_PROGRESS)·발송 대기(WAITING_SHIPMENT)이고 발송 기한이 {@code now} 이하인 주문의 ID 목록을 조회한다. 이 조회는 트랜잭션 밖에서 실행한다.
     *   2. 주문 ID마다 {@link TransactionTemplate}으로 새 트랜잭션을 열고 {@link #cancelIfStillExpired}를 실행한다.
     *   3. 한 건에서 예외가 발생하면 그 건의 트랜잭션만 롤백하고 오류 로그를 남긴 뒤 다음 건을 처리한다.
     *   4. 대상 건수와 취소한 건수를 로그로 남긴다.
     *
     * 이 메서드에는 {@code @Transactional}이 없다. 그래서 2번의 트랜잭션은 주문마다 독립적으로 커밋되거나 롤백된다.
     * 실패한 주문은 상태가 바뀌지 않으므로 다음 실행에서 다시 대상이 된다. 실패한 주문의 재처리·복구 정책은 명세에 없다.
     * 여러 인스턴스에서 동시에 실행되는 경우의 제어는 구현되어 있지 않다.
     *
     * @param now 대상 조회와 상태 재확인에 사용할 현재 시각. {@code TradeScheduler.runJob}이 전달한다.
     * @return 이번 실행에서 취소한 주문 수
     */
    @Override
    public int cancelExpiredUnshipped(Instant now) {
        // 1. 취소 대상 주문 ID 목록 조회. 트랜잭션 밖에서 실행하며 잠금을 걸지 않는다.
        //    호출 흐름: PurchaseOrderRepository.findIdsByDeadlinePassed()
        List<Long> targets = purchaseOrderRepository.findIdsByDeadlinePassed(
                TradeStatus.IN_PROGRESS, ShippingStatus.WAITING_SHIPMENT, now);
        int processed = 0;
        for (Long orderId : targets) {
            try {
                // 2. 주문마다 새 트랜잭션에서 상태를 다시 확인하고 취소한다. 콜백이 정상 종료하면 커밋하고, 예외를 던지면 롤백한다.
                //    호출 흐름: TransactionTemplate.execute() → cancelIfStillExpired() → cancel()
                Boolean cancelled = transactionTemplate.execute(status -> cancelIfStillExpired(orderId, now));
                if (Boolean.TRUE.equals(cancelled)) {
                    processed++;
                }
            } catch (RuntimeException e) {
                // 3. 한 건의 실패는 오류 로그만 남기고 다음 건으로 넘어간다. 이 건의 트랜잭션은 이미 롤백되었다.
                logger.error("[미발송 자동 취소] 처리 실패 orderId={}, reason={}", orderId, e.getMessage());
            }
        }
        // 4. 대상 건수와 취소한 건수 기록
        logger.info("[미발송 자동 취소] 대상={}, 처리={}", targets.size(), processed);
        return processed;
    }

    /**
     * 주문이 아직 미발송 자동 취소 조건을 만족하는지 다시 확인하고, 만족하면 취소한다.
     *
     * 처리 흐름:
     *   1. 주문을 다시 조회한다.
     *   2. 다음 중 하나라도 해당하면 취소하지 않고 {@code false}를 반환한다.
     *      - 주문이 없다.
     *      - 거래 상태가 진행 중(IN_PROGRESS)이 아니다. 대상 조회 뒤에 당사자 취소 등으로 상태가 바뀐 경우다.
     *      - 배송 상태가 발송 대기(WAITING_SHIPMENT)가 아니다. 대상 조회 뒤에 발송이 등록된 경우다.
     *      - 현재 시각이 발송 기한 전이다.
     *      - 발송 정보(SHIPMENT) 행이 있다. 요구사항은 발송 정보 등록 여부로 발송을 판단한다.
     *   3. 취소 주체 {@code SYSTEM}, 취소 사유 {@link #AUTO_CANCEL_REASON}으로 {@link #cancel}을 실행하고 {@code true}를 반환한다.
     *
     * {@link #cancelExpiredUnshipped}가 연 트랜잭션 안에서 실행한다.
     * 상태 검사는 잠금 없이 조회한 값으로 한다. 검사 뒤 이 트랜잭션이 커밋되기 전에 다른 트랜잭션이 커밋한 변경은 검사에 반영되지 않는다.
     * 발송 기한 검사는 대상 조회와 같은 {@code now}로 변경되지 않는 {@code shipDeadlineAt}을 비교하므로, 대상 조회에 포함된 주문은 이 검사를 항상 통과한다.
     *
     * @param orderId 확인할 주문 ID
     * @param now     취소 여부 판단과 취소 시각에 사용할 현재 시각
     * @return 취소했으면 {@code true}, 조건을 만족하지 않아 건너뛰었으면 {@code false}
     * @throws BusinessException 반환 후 구매자 잔액이 저장 범위를 넘으면 {@link #cancel}이 {@code VALIDATION_ERROR}로 던진다.
     */
    private boolean cancelIfStillExpired(Long orderId, Instant now) {
        // 1. 주문 재조회. 이 트랜잭션의 영속성 컨텍스트에 엔티티를 적재하며 잠금은 걸지 않는다.
        PurchaseOrder order = purchaseOrderRepository.findById(orderId).orElse(null);
        // 2. 자동 취소 조건 재확인
        //    호출 흐름: TimeRules.isBeforeDeadline() → ShipmentRepository.existsByOrderId()
        if (order == null
                || order.getTradeStatus() != TradeStatus.IN_PROGRESS
                || order.getShippingStatus() != ShippingStatus.WAITING_SHIPMENT
                || TimeRules.isBeforeDeadline(now, order.getShipDeadlineAt())
                || shipmentRepository.existsByOrderId(orderId)) {
            return false;
        }
        // 3. 공통 취소 처리. 주문을 취소 완료로 바꾸고 구매자에게 결제 금액 전액을 반환한다.
        //    호출 흐름: cancel() → WalletService.ensureCreditable() → PurchaseOrder.cancel() → WalletService.credit() → ConversationService.publishStateToTradeConversation()
        cancel(order, CancelledBy.SYSTEM, AUTO_CANCEL_REASON, now);
        return true;
    }

    /**
     * 주문을 취소 완료(TradeStatus.CANCELLED)로 바꾸고 구매자에게 결제 금액 전액을 반환한다.
     *
     * 당사자 취소({@link #cancelByParty})와 미발송 자동 취소({@link #cancelIfStillExpired})가 함께 사용하는 공통 처리다.
     *
     * 처리 흐름:
     *   1. 구매자 잔액에 결제 금액을 더해도 저장 범위를 넘지 않는지 확인한다. 넘으면 아무것도 바꾸지 않고 예외를 던진다.
     *   2. 주문을 취소 완료(CANCELLED)로 바꾸고 취소 주체, 사유, 종료 시각을 기록한다.
     *   3. 구매자 잔액에 결제 금액 전액을 더하고 {@code CANCEL_REFUND} 내역을 기록한다.
     *   4. 당사자 대화에 대화 상태 이벤트 전송을 등록한다. 실제 전송은 커밋 뒤에 실행된다.
     * 상품의 남은 수량과 판매 상태는 바꾸지 않는다. 취소 뒤에도 재고를 복구하지 않는 정책이다.
     *
     * 호출하는 쪽의 트랜잭션 안에서 실행한다. 주문 상태 검사는 호출하는 쪽이 먼저 한다.
     *
     * @param order  취소할 주문. 호출하는 쪽 트랜잭션에서 조회한 영속 상태의 엔티티
     * @param by     취소 주체. 당사자 취소는 {@code BUYER} 또는 {@code SELLER}, 자동 취소는 {@code SYSTEM}
     * @param reason 취소 사유. 자동 취소는 {@link #AUTO_CANCEL_REASON}
     * @param now    취소 시각. 주문의 종료 시각과 잔액 변동 내역의 발생 시각에 사용한다.
     * @throws BusinessException     반환 후 잔액이 저장 범위를 넘으면 {@code VALIDATION_ERROR}
     * @throws IllegalStateException 주문이 진행 중·발송 대기 상태가 아니면 {@link PurchaseOrder#cancel}이 던진다.
     */
    private void cancel(PurchaseOrder order, CancelledBy by, String reason, Instant now) {
        // 1. 반환 후 잔액의 저장 범위 확인. 상태를 바꾸기 전에 검사하므로 실패하면 아무것도 반영되지 않는다.
        //    호출 흐름: WalletService.ensureCreditable() → currentBalance() → AmountRules.fitsBalance()
        walletService.ensureCreditable(order.getBuyerId(), order.getPaidAmount());

        // 로그에 남길 변경 전 거래 상태
        TradeStatus before = order.getTradeStatus();

        // 2. 주문을 취소 완료로 변경. 엔티티 필드만 바꾸며, UPDATE는 flush 때 dirty checking으로 실행된다.
        //    호출 흐름: PurchaseOrder.cancel() → requireState()
        order.cancel(by, reason, now);

        // 3. 구매자 잔액에 결제 금액 전액을 더하고 CANCEL_REFUND 내역을 기록한다.
        //    호출 흐름: WalletService.credit() → getOrOpen() → Wallet.apply() → Wallet.increase() → BalanceTransaction.record()
        walletService.credit(order.getBuyerId(), order.getPaidAmount(), BalanceTransactionType.CANCEL_REFUND,
                order.getOrderId(), now);
        logger.info("[주문 취소] orderId={}, memberId={}, cancelledBy={}, tradeStatus {} -> {}",
                order.getOrderId(), order.getBuyerId(), by, before, order.getTradeStatus());

        // 4. 당사자 대화의 상태 이벤트 전송 등록. 실제 전송은 커밋이 끝난 뒤 실행된다.
        //    호출 흐름: ConversationService.publishStateToTradeConversation() → stateEvent() → writability() → RealtimePublisher.publish()
        conversationService.publishStateToTradeConversation(order.getProductId(), order.getBuyerId());
    }
}
