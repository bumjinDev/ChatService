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
import com.chatservice.marketplace.order.domain.OrderEnums.RefundDecision;
import com.chatservice.marketplace.order.domain.OrderEnums.RefundReasonCode;
import com.chatservice.marketplace.order.domain.OrderEnums.ShippingStatus;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.PurchaseOrder;
import com.chatservice.marketplace.order.domain.RefundRequest;
import com.chatservice.marketplace.wallet.domain.BalanceTransactionType;
import com.chatservice.marketplace.wallet.service.IWalletService;

/**
 * 환불 요청과 판단(F-017, F-018). 환불 정책은 요구사항 CH-001 을 따른다.
 * 판매자 동의는 반품 없는 전액 환불, 거절은 정상 완료와 판매대금 지급, 접수 후 48시간 무응답은 자동 승인·전액 환불이다.
 * 운영자 판정·재심·이의 제기는 없다.
 */
@Service
public class RefundService implements IRefundService {

    private static final Logger logger = LoggerFactory.getLogger(RefundService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final ITradeCompletionService tradeCompletionService;
    private final IWalletService walletService;
    private final IConversationService conversationService;
    private final TransactionTemplate transactionTemplate;
    private final TimeRules timeRules;

    public RefundService(PurchaseOrderRepository purchaseOrderRepository, RefundRequestRepository refundRequestRepository,
                         ITradeCompletionService tradeCompletionService, IWalletService walletService,
                         IConversationService conversationService, TransactionTemplate transactionTemplate,
                         TimeRules timeRules) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.refundRequestRepository = refundRequestRepository;
        this.tradeCompletionService = tradeCompletionService;
        this.walletService = walletService;
        this.conversationService = conversationService;
        this.transactionTemplate = transactionTemplate;
        this.timeRules = timeRules;
    }

    /**
     * 구매자의 환불 요청을 저장하고 주문의 거래 상태를 보류(ON_HOLD)로 변경한다.
     *
     * 처리 흐름:
     *   1. 주문을 조회하고 요청한 회원이 구매자인지 확인한다.
     *   2. 주문이 환불 요청을 받을 수 있는 상태인지 확인한다. 진행 중·배송 완료 상태이고, 상품 확인 기한 전이며, 기존 환불 요청이 없어야 한다.
     *   3. 환불 요청을 응답 대기(PENDING) 상태로 저장한다. 판매자 응답 기한은 접수 시각에서 48시간 뒤다.
     *   4. 주문의 거래 상태를 보류로 변경한다.
     *
     * 환불 요청 저장과 보류 변경은 한 트랜잭션에서 실행된다. 보류 중에는 구매 확정 요청이 거절되고, 확인 기간 만료 자동 완료도 실행되지 않는다.
     * 보류로 변경해도 대화의 쓰기 가능 여부가 바뀌지 않으므로 대화 상태 이벤트는 보내지 않는다.
     *
     * {@code OrderController.requestRefund}가 호출하며, 이 메서드의 {@code @Transactional}이 새 트랜잭션을 시작한다.
     * 주문 조회와 환불 요청 존재 확인에는 잠금을 걸지 않는다. 같은 주문에 대한 동시 요청의 제어는 구현되어 있지 않다.
     *
     * @param memberId   요청한 회원 ID. 컨트롤러가 인증 정보에서 꺼내 전달한다.
     * @param orderId    환불을 요청할 주문 ID
     * @param reasonCode 환불 사유 코드. 요청 DTO에서 {@code null}이 아닌지 검증했다. 정의된 값은 {@link RefundReasonCode#DESCRIPTION_MISMATCH} 하나다.
     * @param detail     상세 설명. 요청 DTO에서 비어 있지 않고 2000자 이하인지 검증했다.
     * @param requestId  요청 식별자. 선택 값이며 {@code null}일 수 있다.
     * @throws BusinessException
     *           - {@code ORDER_NOT_FOUND}(404): 주문이 없다
     *           - {@code NOT_BUYER}(403): 요청한 회원이 구매자가 아니다
     *           - {@code REFUND_ALREADY_REQUESTED}(409): 거래 상태가 보류이거나 환불 요청이 이미 있다
     *           - {@code TRADE_ALREADY_FINALIZED}(409): 거래 상태가 최종 상태다
     *           - {@code ORDER_NOT_DELIVERED}(409): 배송 상태가 배송 완료가 아니다
     *           - {@code INSPECTION_PERIOD_ENDED}(409): 현재 시각이 상품 확인 기한 이상이다
     * @throws IllegalStateException 주문이 진행 중·배송 완료 상태가 아니면 {@link PurchaseOrder#hold}가 던진다.
     */
    @Override
    @Transactional
    public void requestRefund(String memberId, Long orderId, RefundReasonCode reasonCode, String detail, String requestId) {
        // 1. 현재 시각 조회. 상품 확인 기한 검사, 환불 요청 접수 시각, 판매자 응답 기한 계산에 같은 값을 사용한다.
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
        // 4. 보류 상태 확인. 보류 주문은 상품 확인 기한과 관계없이 REFUND_ALREADY_REQUESTED로 거절한다.
        //    호출 흐름: PurchaseOrder.getTradeStatus()
        if (order.getTradeStatus() == TradeStatus.ON_HOLD) {
            throw new BusinessException(ErrorCode.REFUND_ALREADY_REQUESTED);
        }
        // 5. 최종 상태 확인
        //    호출 흐름: PurchaseOrder.isFinalized() → TradeStatus.isFinal()
        if (order.isFinalized()) {
            throw new BusinessException(ErrorCode.TRADE_ALREADY_FINALIZED);
        }
        // 6. 배송 완료 확인. 상품 확인 기한이 null인 주문은 여기서 거절되어 7번에 도달하지 않는다.
        //    호출 흐름: PurchaseOrder.getShippingStatus()
        if (order.getShippingStatus() != ShippingStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.ORDER_NOT_DELIVERED);
        }
        // 7. 상품 확인 기한 확인. 현재 시각이 상품 확인 기한 이상이면 거절한다.
        //    호출 흐름: TimeRules.isBeforeDeadline()
        if (!TimeRules.isBeforeDeadline(now, order.getInspectionDeadlineAt())) {
            throw new BusinessException(ErrorCode.INSPECTION_PERIOD_ENDED);
        }
        // 8. 환불 요청 중복 확인. 잠금 없이 행 존재 여부만 조회한다.
        //    호출 흐름: RefundRequestRepository.existsByOrderId()
        if (refundRequestRepository.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.REFUND_ALREADY_REQUESTED);
        }
        // 9. 환불 요청 저장. 식별자 생성 전략이 IDENTITY이므로 save 호출 시점에 INSERT가 실행된다.
        //    호출 흐름: TimeRules.refundResponseDeadlineAt() → RefundRequest.submit() → RefundRequestRepository.save()
        refundRequestRepository.save(RefundRequest.submit(orderId, memberId, reasonCode, detail, now,
                timeRules.refundResponseDeadlineAt(now), requestId));
        // 10. 거래 상태를 보류로 변경. PURCHASE_ORDER의 UPDATE는 커밋 전 플러시에서 변경 감지로 실행된다.
        //    호출 흐름: PurchaseOrder.hold() → PurchaseOrder.requireState()
        TradeStatus before = order.getTradeStatus();
        order.hold();
        logger.info("[환불 요청] orderId={}, memberId={}, tradeStatus {} -> {}",
                orderId, memberId, before, order.getTradeStatus());
    }

    /**
     * 판매자의 환불 동의 또는 거절을 기록하고, 동의하면 환불을, 거절하면 거래 정상 완료를 처리한다.
     *
     * 처리 흐름:
     *   1. 주문과 환불 요청을 조회하고 요청한 회원이 판매자인지 확인한다.
     *   2. 판매자 응답을 받을 수 있는 상태인지 확인한다. 환불 요청의 처리 결과가 응답 대기(PENDING)이고, 주문이 보류(ON_HOLD) 상태이며, 판매자 응답 기한 전이어야 한다.
     *   3. 동의하면 처리 결과를 APPROVED로 기록하고, 주문을 환불 완료(REFUNDED)로 변경하며, 결제 금액 전액을 구매자 잔액으로 반환한다.
     *   4. 거절하면 처리 결과를 REJECTED로 기록하고, 주문을 정상 완료(COMPLETED)로 변경하며, 결제 금액 전액을 판매자 잔액에 판매대금으로 지급한다.
     *
     * 잔액 저장 범위 확인은 처리 결과를 기록하기 전에 한다. 거절 경로에서는 {@link ITradeCompletionService#complete}가 판매자 잔액을 한 번 더 확인한다.
     * 두 경로 모두 주문이 최종 상태가 되므로 거래 당사자 대화의 상태 이벤트 전송을 등록한다.
     *
     * {@code OrderController.approveRefund}와 {@code OrderController.rejectRefund}가 호출하며, 이 메서드의 {@code @Transactional}이 새 트랜잭션을 시작한다.
     * 거절 경로의 {@link ITradeCompletionService#complete}는 다른 빈의 메서드라 프록시를 거치며, REQUIRED 전파로 이 트랜잭션에 참여한다.
     * 주문과 환불 요청 조회에는 잠금을 걸지 않는다. 같은 환불 요청에 대한 동시 응답과, 판매자 응답과 무응답 자동 환불이 동시에 처리되는 경우의 제어는 구현되어 있지 않다.
     *
     * @param memberId 요청한 회원 ID. 컨트롤러가 인증 정보에서 꺼내 전달한다.
     * @param orderId  환불 요청이 접수된 주문 ID
     * @param approve  {@code true}이면 환불 동의, {@code false}이면 환불 거절
     * @throws BusinessException
     *           - {@code REFUND_REQUEST_NOT_FOUND}(404): 주문이 없거나 환불 요청이 없다
     *           - {@code NOT_SELLER}(403): 요청한 회원이 판매자가 아니다
     *           - {@code REFUND_ALREADY_DECIDED}(409): 환불 요청의 처리 결과가 이미 기록되었다
     *           - {@code ORDER_NOT_ON_HOLD}(409): 거래 상태가 보류가 아니다
     *           - {@code REFUND_RESPONSE_DEADLINE_PASSED}(409): 현재 시각이 판매자 응답 기한 이상이다
     *           - {@code VALIDATION_ERROR}(400): 반환 또는 지급 후 잔액이 저장 범위를 넘는다
     * @throws IllegalStateException 환불 요청이나 주문이 상태 전환을 허용하지 않으면 {@link RefundRequest#decide}, {@link PurchaseOrder#refund}, {@link PurchaseOrder#complete}가 던진다.
     */
    @Override
    @Transactional
    public void respond(String memberId, Long orderId, boolean approve) {
        // 1. 현재 시각 조회. 판매자 응답 기한 검사와 처리 시각에 같은 값을 사용한다.
        //    호출 흐름: TimeRules.now()
        Instant now = timeRules.now();
        // 2. 주문 조회. 설계 명세서의 이 API 오류 목록에 ORDER_NOT_FOUND가 없으므로 주문이 없어도 REFUND_REQUEST_NOT_FOUND를 던진다. 잠금은 걸지 않는다.
        //    호출 흐름: PurchaseOrderRepository.findById()
        PurchaseOrder order = purchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
        // 3. 판매자 확인
        //    호출 흐름: PurchaseOrder.isSeller()
        if (!order.isSeller(memberId)) {
            throw new BusinessException(ErrorCode.NOT_SELLER);
        }
        // 4. 환불 요청 조회. 잠금은 걸지 않는다.
        //    호출 흐름: RefundRequestRepository.findByOrderId()
        RefundRequest request = refundRequestRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
        // 5. 처리 결과 확인. 응답 대기(PENDING)가 아니면 판매자 응답이나 자동 승인으로 이미 처리된 요청이다.
        //    호출 흐름: RefundRequest.isPending()
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.REFUND_ALREADY_DECIDED);
        }
        // 6. 보류 상태 확인
        //    호출 흐름: PurchaseOrder.getTradeStatus()
        if (order.getTradeStatus() != TradeStatus.ON_HOLD) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ON_HOLD);
        }
        // 7. 판매자 응답 기한 확인. 현재 시각이 응답 기한 이상이면 거절한다. 이 시각부터는 무응답 자동 환불의 대상이다.
        //    호출 흐름: TimeRules.isBeforeDeadline()
        if (!TimeRules.isBeforeDeadline(now, request.getResponseDeadlineAt())) {
            throw new BusinessException(ErrorCode.REFUND_RESPONSE_DEADLINE_PASSED);
        }
        if (approve) {
            // 8. 환불 동의. 구매자 잔액의 저장 범위를 확인하고, 처리 결과를 APPROVED로 기록한 뒤 환불 완료를 처리한다.
            //    호출 흐름: WalletService.ensureCreditable() → decide() → RefundRequest.decide() → refund() → PurchaseOrder.refund() → WalletService.credit()
            walletService.ensureCreditable(order.getBuyerId(), order.getPaidAmount());
            decide(request, RefundDecision.APPROVED, now);
            refund(order, now);
        } else {
            // 9. 환불 거절. 판매자 잔액의 저장 범위를 확인하고, 처리 결과를 REJECTED로 기록한 뒤 거래 정상 완료와 판매대금 지급을 처리한다.
            //    호출 흐름: WalletService.ensureCreditable() → decide() → RefundRequest.decide() → TradeCompletionService.complete() → PurchaseOrder.complete() → WalletService.credit()
            walletService.ensureCreditable(order.getSellerId(), order.getPaidAmount());
            decide(request, RefundDecision.REJECTED, now);
            tradeCompletionService.complete(order, CompletionCause.REFUND_REJECTED, now);
        }
    }

    /**
     * 판매자가 응답 기한까지 응답하지 않은 환불 요청을 찾아 자동 승인하고 구매자에게 결제 금액 전액을 반환한다.
     *
     * 응답 기한은 환불 요청 접수 시각에서 48시간 뒤다.
     *
     * 처리 흐름:
     *   1. 처리 결과가 응답 대기(PENDING)이고, 주문이 보류(ON_HOLD)이며, 응답 기한이 {@code now} 이하인 환불 요청의 ID 목록을 조회한다. 이 조회는 트랜잭션 밖에서 실행한다.
     *   2. 환불 요청 ID마다 {@link TransactionTemplate}으로 새 트랜잭션을 열고 {@link #autoApproveIfExpired}를 실행한다.
     *   3. 한 건에서 예외가 발생하면 그 건의 트랜잭션만 롤백하고 오류 로그를 남긴 뒤 다음 건을 처리한다.
     *   4. 대상 건수와 환불 완료한 건수를 로그로 남긴다.
     *
     * 다른 자동 처리와 달리 주문 ID가 아니라 환불 요청 ID 단위로 처리한다.
     * 이 메서드에는 {@code @Transactional}이 없다. 그래서 2번의 트랜잭션은 환불 요청마다 독립적으로 커밋되거나 롤백된다.
     * 실패한 요청은 상태가 바뀌지 않으므로 다음 실행에서 다시 대상이 된다. 실패한 요청의 재처리·복구 정책은 명세에 없다.
     * 여러 인스턴스에서 동시에 실행되는 경우의 제어는 구현되어 있지 않다.
     *
     * @param now 대상 조회와 상태 재확인에 사용할 현재 시각. {@code TradeScheduler.runJob}이 전달한다.
     * @return 이번 실행에서 환불 완료한 건수
     */
    @Override
    public int autoApproveExpired(Instant now) {
        // 1. 자동 환불 대상 환불 요청 ID 목록 조회. 트랜잭션 밖에서 실행하며 잠금을 걸지 않는다.
        //    호출 흐름: RefundRequestRepository.findIdsByResponseExpired()
        List<Long> targets = refundRequestRepository.findIdsByResponseExpired(RefundDecision.PENDING, TradeStatus.ON_HOLD, now);
        int processed = 0;
        for (Long refundRequestId : targets) {
            try {
                // 2. 환불 요청마다 새 트랜잭션에서 상태를 다시 확인하고 자동 승인·환불한다. 콜백이 정상 종료하면 커밋하고, 예외를 던지면 롤백한다.
                //    호출 흐름: TransactionTemplate.execute() → autoApproveIfExpired() → decide() → refund()
                Boolean refunded = transactionTemplate.execute(status -> autoApproveIfExpired(refundRequestId, now));
                if (Boolean.TRUE.equals(refunded)) {
                    processed++;
                }
            } catch (RuntimeException e) {
                // 3. 한 건의 실패는 오류 로그만 남기고 다음 건으로 넘어간다. 이 건의 트랜잭션은 이미 롤백되었다.
                logger.error("[무응답 자동 환불] 처리 실패 refundRequestId={}, reason={}", refundRequestId, e.getMessage());
            }
        }
        // 4. 대상 건수와 환불 완료한 건수 기록
        logger.info("[무응답 자동 환불] 대상={}, 처리={}", targets.size(), processed);
        return processed;
    }

    /**
     * 환불 요청이 아직 무응답 자동 환불 조건을 만족하는지 다시 확인하고, 만족하면 자동 승인하고 환불한다.
     *
     * 처리 흐름:
     *   1. 환불 요청을 다시 조회한다. 요청이 없거나, 이미 처리 결과가 기록되었거나, 현재 시각이 응답 기한 전이면 {@code false}를 반환한다.
     *   2. 주문을 다시 조회한다. 주문이 없거나 거래 상태가 보류(ON_HOLD)가 아니면 {@code false}를 반환한다.
     *   3. 구매자 잔액에 결제 금액을 더해도 저장 범위를 넘지 않는지 확인한다. 넘으면 아무것도 바꾸지 않고 예외를 던진다.
     *   4. 환불 요청의 처리 결과를 자동 승인({@code AUTO_APPROVED})으로 기록한다.
     *   5. 주문을 환불 완료로 바꾸고 구매자에게 결제 금액 전액을 반환한 뒤 {@code true}를 반환한다.
     *
     * {@link #autoApproveExpired}가 연 트랜잭션 안에서 실행한다. 상태 검사는 잠금 없이 조회한 값으로 한다.
     * 판매자 응답과 자동 환불은 처리 결과 기록과 주문 상태 변경을 한 트랜잭션에서 한다. 그래서 커밋된 데이터에서 응답 대기인 요청의 주문은 항상 보류 상태이고, 1번의 응답 대기 검사를 통과한 요청은 2번의 보류 검사도 통과한다.
     * 응답 기한 검사는 대상 조회와 같은 {@code now}로 변경되지 않는 {@code responseDeadlineAt}을 비교하므로, 대상 조회에 포함된 요청은 이 검사를 항상 통과한다.
     * 판매자 응답은 현재 시각이 응답 기한보다 이를 때만 허용되므로, 같은 시각에 이 자동 환불과 함께 허용되지 않는다.
     *
     * @param refundRequestId 확인할 환불 요청 ID
     * @param now             자동 환불 여부 판단, 처리 결과 기록 시각, 거래 종료 시각에 사용할 현재 시각
     * @return 환불 완료했으면 {@code true}, 조건을 만족하지 않아 건너뛰었으면 {@code false}
     * @throws BusinessException 반환 후 구매자 잔액이 저장 범위를 넘으면 {@code VALIDATION_ERROR}
     */
    private boolean autoApproveIfExpired(Long refundRequestId, Instant now) {
        // 1. 환불 요청 재조회와 자동 환불 조건 재확인. 잠금은 걸지 않는다.
        //    호출 흐름: RefundRequestRepository.findById() → RefundRequest.isPending() → TimeRules.isBeforeDeadline()
        RefundRequest request = refundRequestRepository.findById(refundRequestId).orElse(null);
        if (request == null || !request.isPending() || TimeRules.isBeforeDeadline(now, request.getResponseDeadlineAt())) {
            return false;
        }
        // 2. 주문 재조회와 보류 상태 확인. 잠금은 걸지 않는다.
        PurchaseOrder order = purchaseOrderRepository.findById(request.getOrderId()).orElse(null);
        if (order == null || order.getTradeStatus() != TradeStatus.ON_HOLD) {
            return false;
        }
        // 3. 반환 후 잔액의 저장 범위 확인. 상태를 바꾸기 전에 검사하므로 실패하면 아무것도 반영되지 않는다.
        //    호출 흐름: WalletService.ensureCreditable() → currentBalance() → AmountRules.fitsBalance()
        walletService.ensureCreditable(order.getBuyerId(), order.getPaidAmount());
        // 4. 환불 요청의 처리 결과를 자동 승인으로 기록
        //    호출 흐름: decide() → RefundRequest.decide()
        decide(request, RefundDecision.AUTO_APPROVED, now);
        // 5. 공통 환불 처리. 주문을 환불 완료로 바꾸고 구매자에게 결제 금액 전액을 반환한다.
        //    호출 흐름: refund() → PurchaseOrder.refund() → WalletService.credit() → ConversationService.publishStateToTradeConversation()
        refund(order, now);
        return true;
    }

    /**
     * 환불 요청의 처리 결과와 처리 시각을 기록하고 로그를 남긴다.
     *
     * 판매자 동의·거절({@link #respond})과 무응답 자동 환불({@link #autoApproveIfExpired})이 함께 사용한다.
     * 엔티티 필드만 바꾸며, UPDATE는 flush 때 dirty checking으로 실행된다.
     *
     * @param request  처리 결과를 기록할 환불 요청. 호출하는 쪽 트랜잭션에서 조회한 영속 상태의 엔티티
     * @param decision 처리 결과. {@code APPROVED}, {@code REJECTED}, {@code AUTO_APPROVED} 중 하나
     * @param now      처리 시각(DECIDED_AT)
     * @throws IllegalStateException 이미 처리 결과가 기록된 요청이거나 {@code decision}이 {@code PENDING}이면 {@link RefundRequest#decide}가 던진다.
     */
    private void decide(RefundRequest request, RefundDecision decision, Instant now) {
        request.decide(decision, now);
        logger.info("[환불 판단] orderId={}, refundRequestId={}, decision {} -> {}",
                request.getOrderId(), request.getRefundRequestId(), RefundDecision.PENDING, decision);
    }

    /**
     * 주문을 환불 완료(REFUNDED)로 바꾸고 구매자에게 결제 금액 전액을 반환한다.
     *
     * 판매자 환불 동의({@link #respond})와 무응답 자동 환불({@link #autoApproveIfExpired})이 함께 사용하는 공통 처리다.
     *
     * 처리 흐름:
     *   1. 주문을 환불 완료로 바꾸고 거래 종료 시각을 기록한다.
     *   2. 구매자 잔액에 결제 금액 전액을 더하고 {@code REFUND} 내역을 기록한다.
     *   3. 당사자 대화에 대화 상태 이벤트 전송을 등록한다. 실제 전송은 커밋 뒤에 실행된다.
     * 상품의 남은 수량과 판매 상태는 바꾸지 않는다. 환불 뒤에도 재고를 복구하지 않는 정책이다.
     *
     * 호출하는 쪽의 트랜잭션 안에서 실행한다.
     * 반환 후 잔액의 저장 범위 확인과 환불 요청의 처리 결과 기록은 호출하는 쪽이 이 메서드보다 먼저 한다.
     *
     * @param order 환불할 주문. 호출하는 쪽 트랜잭션에서 조회한 영속 상태의 엔티티
     * @param now   거래 종료 시각. 잔액 변동 내역의 발생 시각에도 사용한다.
     * @throws IllegalStateException 주문이 보류(ON_HOLD)가 아니면 {@link PurchaseOrder#refund}가 던진다.
     */
    private void refund(PurchaseOrder order, Instant now) {
        // 로그에 남길 변경 전 거래 상태
        TradeStatus before = order.getTradeStatus();

        // 1. 주문을 환불 완료로 변경. 엔티티 필드만 바꾸며, UPDATE는 flush 때 dirty checking으로 실행된다.
        //    호출 흐름: PurchaseOrder.refund() → requireState()
        order.refund(now);

        // 2. 구매자 잔액에 결제 금액 전액을 더하고 REFUND 내역을 기록한다.
        //    호출 흐름: WalletService.credit() → getOrOpen() → Wallet.apply() → Wallet.increase() → BalanceTransaction.record()
        walletService.credit(order.getBuyerId(), order.getPaidAmount(), BalanceTransactionType.REFUND,
                order.getOrderId(), now);
        logger.info("[환불 완료] orderId={}, memberId={}, tradeStatus {} -> {}",
                order.getOrderId(), order.getBuyerId(), before, order.getTradeStatus());

        // 3. 당사자 대화의 상태 이벤트 전송 등록. 실제 전송은 커밋이 끝난 뒤 실행된다.
        //    호출 흐름: ConversationService.publishStateToTradeConversation() → stateEvent() → writability() → RealtimePublisher.publish()
        conversationService.publishStateToTradeConversation(order.getProductId(), order.getBuyerId());
    }
}
