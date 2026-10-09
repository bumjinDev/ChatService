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
import com.chatservice.marketplace.order.RefundRequestRepository;
import com.chatservice.marketplace.order.domain.OrderEnums.CompletionCause;
import com.chatservice.marketplace.order.domain.OrderEnums.ShippingStatus;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.PurchaseOrder;
import com.chatservice.marketplace.wallet.domain.BalanceTransactionType;
import com.chatservice.marketplace.wallet.service.IWalletService;

@Service
public class TradeCompletionService implements ITradeCompletionService {

    private static final Logger logger = LoggerFactory.getLogger(TradeCompletionService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final IWalletService walletService;
    private final IConversationService conversationService;
    private final TransactionTemplate transactionTemplate;
    private final TimeRules timeRules;

    public TradeCompletionService(PurchaseOrderRepository purchaseOrderRepository,
                                  RefundRequestRepository refundRequestRepository, IWalletService walletService,
                                  IConversationService conversationService, TransactionTemplate transactionTemplate,
                                  TimeRules timeRules) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.refundRequestRepository = refundRequestRepository;
        this.walletService = walletService;
        this.conversationService = conversationService;
        this.transactionTemplate = transactionTemplate;
        this.timeRules = timeRules;
    }

    /**
     * 구매자의 구매 확정 요청을 처리해 주문을 정상 완료(COMPLETED)로 변경하고 판매자에게 판매대금을 지급한다.
     *
     * 처리 흐름:
     *   1. 현재 시각을 구한다. 이 값을 상품 확인 기한 검사와 거래 종료 시각, 잔액 변동 내역의 발생 시각에 함께 사용한다.
     *   2. 주문을 조회한다. 주문이 없으면 {@code ORDER_NOT_FOUND}를 던진다.
     *   3. 요청한 회원이 주문의 구매자인지 확인한다. 구매자가 아니면 {@code NOT_BUYER}를 던진다.
     *   4. 거래 상태가 보류(ON_HOLD)이면 {@code ORDER_ON_HOLD}를 던진다.
     *   5. 거래 상태가 최종 상태(COMPLETED, REFUNDED, CANCELLED)이면 {@code TRADE_ALREADY_FINALIZED}를 던진다.
     *   6. 배송 상태가 배송 완료(DELIVERED)가 아니면 {@code ORDER_NOT_DELIVERED}를 던진다.
     *   7. 현재 시각이 상품 확인 기한 이상이면 {@code INSPECTION_PERIOD_ENDED}를 던진다.
     *   8. 완료 사유 {@code BUYER_CONFIRMED}로 {@link #complete}를 실행한다.
     *      - 지급 후 판매자 잔액이 저장 범위를 넘지 않는지 확인한다.
     *      - 주문을 정상 완료로 변경한다.
     *      - 판매자 잔액에 결제 금액 전액을 더하고 {@code SALE_PAYOUT} 내역을 기록한다.
     *      - 거래 당사자 대화의 상태 이벤트 전송을 등록한다.
     *
     * 4~6번 검사를 통과한 주문은 진행 중(IN_PROGRESS)·배송 완료 상태다.
     * - 보류 주문도 배송 완료 상태이므로 4번 검사가 없으면 5번과 6번을 통과한다. 4번 검사가 보류 주문을 별도 에러 코드로 거절한다.
     * - 상품 확인 기한은 배송 완료로 전환할 때 저장되며, 그 전에는 {@code null}이다. 6번 검사를 7번보다 먼저 하므로 기한이 {@code null}인 주문은 7번에 도달하지 않는다.
     *
     * {@code OrderController.confirmReceipt}가 호출한다. 컨트롤러에는 트랜잭션이 없으므로 이 메서드의 {@code @Transactional}이 새 트랜잭션을 시작한다.
     * {@link #complete}는 같은 클래스에서 호출하므로 그 메서드의 {@code @Transactional}은 적용되지 않는다. 8번은 이 메서드의 트랜잭션에서 실행된다.
     * 주문 조회에는 잠금을 걸지 않는다. 이미 완료된 주문에 다시 요청하면 5번 검사에서 거절되므로, 요청이 차례로 처리되는 경우에는 판매대금이 한 번만 지급된다.
     * 같은 주문에 구매 확정 요청이 동시에 처리되는 경우와, 구매 확정과 환불 요청·확인 기간 만료 자동 완료가 동시에 처리되는 경우의 제어는 구현되어 있지 않다.
     *
     * @param memberId 요청한 회원 ID. 컨트롤러가 인증 정보에서 꺼내 전달한다.
     * @param orderId  구매 확정할 주문 ID
     * @throws BusinessException
     *           - {@code ORDER_NOT_FOUND}(404): 주문이 없다
     *           - {@code NOT_BUYER}(403): 요청한 회원이 구매자가 아니다
     *           - {@code ORDER_ON_HOLD}(409): 환불 요청으로 보류 중이다
     *           - {@code TRADE_ALREADY_FINALIZED}(409): 거래 상태가 최종 상태다
     *           - {@code ORDER_NOT_DELIVERED}(409): 배송 상태가 배송 완료가 아니다
     *           - {@code INSPECTION_PERIOD_ENDED}(409): 현재 시각이 상품 확인 기한 이상이다
     *           - {@code VALIDATION_ERROR}(400): 지급 후 판매자 잔액이 저장 범위를 넘는다. {@link #complete}가 던진다.
     * @throws IllegalStateException 주문이 진행 중·배송 완료 상태가 아니면 {@link PurchaseOrder#complete}가 던진다.
     */
    @Override
    @Transactional
    public void confirmReceipt(String memberId, Long orderId) {
        // 1. 현재 시각 조회. 상품 확인 기한 검사, 거래 종료 시각, 잔액 변동 내역의 발생 시각에 같은 값을 사용한다.
        //    호출 흐름: TimeRules.now()
        Instant now = timeRules.now();
        // 2. 주문 조회. 이 트랜잭션의 영속성 컨텍스트에 엔티티를 적재하며 잠금은 걸지 않는다.
        //    호출 흐름: PurchaseOrderRepository.findById()
        PurchaseOrder order = purchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        // 3. 구매자 확인
        //    호출 흐름: PurchaseOrder.isBuyer()
        if (!order.isBuyer(memberId)) {
            throw new BusinessException(ErrorCode.NOT_BUYER);
        }
        // 4. 보류 상태 확인. 보류 주문은 최종 상태가 아니므로 5번 검사보다 먼저 별도 에러 코드로 거절한다.
        if (order.getTradeStatus() == TradeStatus.ON_HOLD) {
            throw new BusinessException(ErrorCode.ORDER_ON_HOLD);
        }
        // 5. 최종 상태 확인
        //    호출 흐름: PurchaseOrder.isFinalized() → TradeStatus.isFinal()
        if (order.isFinalized()) {
            throw new BusinessException(ErrorCode.TRADE_ALREADY_FINALIZED);
        }
        // 6. 배송 완료 확인. 배송 완료 전 주문은 상품 확인 기한이 null이므로 7번보다 먼저 검사한다.
        if (order.getShippingStatus() != ShippingStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.ORDER_NOT_DELIVERED);
        }
        // 7. 상품 확인 기한 확인. 현재 시각이 기한 이상이면 거절한다.
        //    호출 흐름: TimeRules.isBeforeDeadline()
        if (!TimeRules.isBeforeDeadline(now, order.getInspectionDeadlineAt())) {
            throw new BusinessException(ErrorCode.INSPECTION_PERIOD_ENDED);
        }
        // 8. 공통 완료 처리. 주문을 정상 완료로 바꾸고 판매자에게 결제 금액 전액을 지급한다.
        //    호출 흐름: complete() → WalletService.ensureCreditable() → PurchaseOrder.complete() → WalletService.credit() → ConversationService.publishStateToTradeConversation()
        complete(order, CompletionCause.BUYER_CONFIRMED, now);
    }

    /**
     * 상품 확인 기한이 지나도록 구매 확정과 환불 요청이 없는 주문을 찾아 자동으로 정상 완료한다.
     *
     * 처리 흐름:
     *   1. 진행 중(IN_PROGRESS)·배송 완료(DELIVERED)이고 상품 확인 기한이 {@code now} 이하인 주문의 ID 목록을 조회한다. 이 조회는 트랜잭션 밖에서 실행한다.
     *   2. 주문 ID마다 {@link TransactionTemplate}으로 새 트랜잭션을 열고 {@link #completeIfExpired}를 실행한다.
     *   3. 한 건에서 예외가 발생하면 그 건의 트랜잭션만 롤백하고 오류 로그를 남긴 뒤 다음 건을 처리한다.
     *   4. 대상 건수와 정상 완료한 건수를 로그로 남긴다.
     *
     * 기한 안에 환불 요청이 접수된 주문은 보류(ON_HOLD) 상태이므로 1번의 조회 조건에서 제외된다.
     * 이 메서드에는 {@code @Transactional}이 없다. 그래서 2번의 트랜잭션은 주문마다 독립적으로 커밋되거나 롤백된다.
     * 실패한 주문은 상태가 바뀌지 않으므로 다음 실행에서 다시 대상이 된다. 실패한 주문의 재처리·복구 정책은 명세에 없다.
     * 여러 인스턴스에서 동시에 실행되는 경우의 제어는 구현되어 있지 않다.
     *
     * @param now 대상 조회와 상태 재확인에 사용할 현재 시각. {@code TradeScheduler.runJob}이 전달한다.
     * @return 이번 실행에서 정상 완료한 주문 수
     */
    @Override
    public int completeExpiredInspections(Instant now) {
        // 1. 자동 완료 대상 주문 ID 목록 조회. 트랜잭션 밖에서 실행하며 잠금을 걸지 않는다.
        //    호출 흐름: PurchaseOrderRepository.findIdsByInspectionExpired()
        List<Long> targets = purchaseOrderRepository.findIdsByInspectionExpired(
                TradeStatus.IN_PROGRESS, ShippingStatus.DELIVERED, now);
        int processed = 0;
        for (Long orderId : targets) {
            try {
                // 2. 주문마다 새 트랜잭션에서 상태를 다시 확인하고 정상 완료한다. 콜백이 정상 종료하면 커밋하고, 예외를 던지면 롤백한다.
                //    호출 흐름: TransactionTemplate.execute() → completeIfExpired() → complete()
                Boolean completed = transactionTemplate.execute(status -> completeIfExpired(orderId, now));
                if (Boolean.TRUE.equals(completed)) {
                    processed++;
                }
            } catch (RuntimeException e) {
                // 3. 한 건의 실패는 오류 로그만 남기고 다음 건으로 넘어간다. 이 건의 트랜잭션은 이미 롤백되었다.
                logger.error("[확인 기간 만료 자동 완료] 처리 실패 orderId={}, reason={}", orderId, e.getMessage());
            }
        }
        // 4. 대상 건수와 정상 완료한 건수 기록
        logger.info("[확인 기간 만료 자동 완료] 대상={}, 처리={}", targets.size(), processed);
        return processed;
    }

    /**
     * 주문이 아직 확인 기간 만료 자동 완료 조건을 만족하는지 다시 확인하고, 만족하면 정상 완료한다.
     *
     * 처리 흐름:
     *   1. 주문을 다시 조회한다.
     *   2. 다음 중 하나라도 해당하면 완료하지 않고 {@code false}를 반환한다.
     *      - 주문이 없다.
     *      - 거래 상태가 진행 중(IN_PROGRESS)이 아니다. 대상 조회 뒤에 구매 확정이나 환불 요청으로 상태가 바뀐 경우다.
     *      - 배송 상태가 배송 완료(DELIVERED)가 아니다.
     *      - 현재 시각이 상품 확인 기한 전이다.
     *      - 환불 요청(REFUND_REQUEST) 행이 있다.
     *   3. 완료 사유 {@code AUTO_EXPIRED}로 {@link #complete}를 실행하고 {@code true}를 반환한다.
     *
     * {@link #completeExpiredInspections}가 연 트랜잭션 안에서 실행한다. 상태 검사는 잠금 없이 조회한 값으로 한다.
     * 환불 요청 접수는 환불 요청 행 생성과 보류(ON_HOLD) 변경을 한 트랜잭션에서 한다. 그래서 커밋된 데이터에서 진행 중인 주문에는 환불 요청 행이 없고, 거래 상태 검사를 통과한 주문은 환불 요청 행 검사도 통과한다.
     * 상품 확인 기한 검사는 대상 조회와 같은 {@code now}로 변경되지 않는 {@code inspectionDeadlineAt}을 비교하므로, 대상 조회에 포함된 주문은 이 검사를 항상 통과한다.
     * 구매 확정과 환불 요청은 현재 시각이 상품 확인 기한보다 이를 때만 허용되므로, 같은 시각에 이 자동 완료와 함께 허용되지 않는다.
     *
     * @param orderId 확인할 주문 ID
     * @param now     완료 여부 판단과 거래 종료 시각에 사용할 현재 시각
     * @return 정상 완료했으면 {@code true}, 조건을 만족하지 않아 건너뛰었으면 {@code false}
     * @throws BusinessException 지급 후 판매자 잔액이 저장 범위를 넘으면 {@link #complete}가 {@code VALIDATION_ERROR}로 던진다.
     */
    private boolean completeIfExpired(Long orderId, Instant now) {
        // 1. 주문 재조회. 이 트랜잭션의 영속성 컨텍스트에 엔티티를 적재하며 잠금은 걸지 않는다.
        PurchaseOrder order = purchaseOrderRepository.findById(orderId).orElse(null);
        // 2. 자동 완료 조건 재확인
        //    호출 흐름: TimeRules.isBeforeDeadline() → RefundRequestRepository.existsByOrderId()
        if (order == null
                || order.getTradeStatus() != TradeStatus.IN_PROGRESS
                || order.getShippingStatus() != ShippingStatus.DELIVERED
                || TimeRules.isBeforeDeadline(now, order.getInspectionDeadlineAt())
                || refundRequestRepository.existsByOrderId(orderId)) {
            return false;
        }
        // 3. 공통 완료 처리. 주문을 정상 완료로 바꾸고 판매자에게 판매대금을 지급한다.
        //    호출 흐름: complete() → WalletService.ensureCreditable() → PurchaseOrder.complete() → WalletService.credit() → ConversationService.publishStateToTradeConversation()
        complete(order, CompletionCause.AUTO_EXPIRED, now);
        return true;
    }

    /**
     * 주문을 정상 완료(COMPLETED)로 바꾸고 판매자에게 결제 금액 전액을 판매대금으로 지급한다.
     *
     * 구매 확정({@link #confirmReceipt}), 확인 기간 만료 자동 완료({@link #completeIfExpired}), 판매자 환불 거절({@code RefundService.respond})이 함께 사용하는 공통 처리다.
     *
     * 처리 흐름:
     *   1. 판매자 잔액에 결제 금액을 더해도 저장 범위를 넘지 않는지 확인한다. 넘으면 아무것도 바꾸지 않고 예외를 던진다.
     *   2. 주문을 정상 완료(COMPLETED)로 바꾸고 완료 사유와 거래 종료 시각을 기록한다.
     *   3. 판매자 잔액에 결제 금액 전액을 더하고 {@code SALE_PAYOUT} 내역을 기록한다.
     *   4. 당사자 대화에 대화 상태 이벤트 전송을 등록한다. 실제 전송은 커밋 뒤에 실행된다.
     *
     * 호출하는 쪽의 트랜잭션 안에서 실행한다. 주문 상태와 기한 검사는 호출하는 쪽이 먼저 한다.
     * - 같은 클래스의 {@link #confirmReceipt}, {@link #completeIfExpired}에서 호출하면 프록시를 거치지 않으므로 이 메서드의 {@code @Transactional}이 적용되지 않는다. 호출하는 쪽이 이미 연 트랜잭션에서 실행된다.
     * - {@code RefundService.respond}에서 호출하면 프록시를 거치며, 전파 속성 REQUIRED에 따라 {@code respond}의 트랜잭션에 참여한다.
     *
     * @param order 완료할 주문. 호출하는 쪽 트랜잭션에서 조회한 영속 상태의 엔티티
     * @param cause 정상 완료 사유. 구매 확정은 {@code BUYER_CONFIRMED}, 자동 완료는 {@code AUTO_EXPIRED}, 환불 거절은 {@code REFUND_REJECTED}
     * @param now   거래 종료 시각. 잔액 변동 내역의 발생 시각에도 사용한다.
     * @throws BusinessException     지급 후 판매자 잔액이 저장 범위를 넘으면 {@code VALIDATION_ERROR}
     * @throws IllegalStateException 완료 사유와 현재 상태의 조합이 허용되지 않으면 {@link PurchaseOrder#complete}가 던진다.
     */
    @Override
    @Transactional
    public void complete(PurchaseOrder order, CompletionCause cause, Instant now) {
        // 1. 지급 후 잔액의 저장 범위 확인. 상태를 바꾸기 전에 검사하므로 실패하면 아무것도 반영되지 않는다.
        //    호출 흐름: WalletService.ensureCreditable() → currentBalance() → AmountRules.fitsBalance()
        walletService.ensureCreditable(order.getSellerId(), order.getPaidAmount());

        // 로그에 남길 변경 전 거래 상태
        TradeStatus before = order.getTradeStatus();

        // 2. 주문을 정상 완료로 변경. 엔티티 필드만 바꾸며, UPDATE는 flush 때 dirty checking으로 실행된다.
        //    호출 흐름: PurchaseOrder.complete() → requireState()
        order.complete(cause, now);

        // 3. 판매자 잔액에 결제 금액 전액을 더하고 SALE_PAYOUT 내역을 기록한다.
        //    호출 흐름: WalletService.credit() → getOrOpen() → Wallet.apply() → Wallet.increase() → BalanceTransaction.record()
        walletService.credit(order.getSellerId(), order.getPaidAmount(), BalanceTransactionType.SALE_PAYOUT,
                order.getOrderId(), now);
        logger.info("[정상 완료] orderId={}, memberId={}, cause={}, tradeStatus {} -> {}",
                order.getOrderId(), order.getSellerId(), cause, before, order.getTradeStatus());

        // 4. 당사자 대화의 상태 이벤트 전송 등록. 실제 전송은 커밋이 끝난 뒤 실행된다.
        //    호출 흐름: ConversationService.publishStateToTradeConversation() → stateEvent() → writability() → RealtimePublisher.publish()
        conversationService.publishStateToTradeConversation(order.getProductId(), order.getBuyerId());
    }
}
