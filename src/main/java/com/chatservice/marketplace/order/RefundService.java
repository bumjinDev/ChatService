package com.chatservice.marketplace.order;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.common.TimeRules;
import com.chatservice.marketplace.conversation.ConversationNotifier;
import com.chatservice.marketplace.wallet.IWalletService;
import com.chatservice.marketplace.wallet.TransactionType;

/** 환불 요청과 거래 보류(F-017), 판매자 환불 응답과 무응답 자동 환불(F-018). */
@Service
public class RefundService implements IRefundService {

	private static final Logger log = LoggerFactory.getLogger(RefundService.class);

	private final OrderAccess orderAccess;
	private final PurchaseOrderRepository orderRepository;
	private final RefundRequestRepository refundRequestRepository;
	private final ITradeCompletionService completionService;
	private final IWalletService walletService;
	private final ConversationNotifier conversationNotifier;
	private final TransactionTemplate transactionTemplate;
	private final OrderDetailAssembler assembler;
	private final TimeRules timeRules;
	private final Clock clock;

	public RefundService(OrderAccess orderAccess, PurchaseOrderRepository orderRepository,
			RefundRequestRepository refundRequestRepository, ITradeCompletionService completionService,
			IWalletService walletService, ConversationNotifier conversationNotifier,
			PlatformTransactionManager transactionManager, OrderDetailAssembler assembler, TimeRules timeRules,
			Clock clock) {
		this.orderAccess = orderAccess;
		this.orderRepository = orderRepository;
		this.refundRequestRepository = refundRequestRepository;
		this.completionService = completionService;
		this.walletService = walletService;
		this.conversationNotifier = conversationNotifier;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.assembler = assembler;
		this.timeRules = timeRules;
		this.clock = clock;
	}

	/**
	 * 처리 순서(설계 명세서 6.17절)
	 * 1. 주문이 없으면 404, 구매자가 아니면 403 NOT_BUYER
	 * 2. 보류 중이면 409 REFUND_ALREADY_REQUESTED, 최종 상태이면 409 TRADE_ALREADY_FINALIZED
	 * 3. 배송 완료가 아니면 409 ORDER_NOT_DELIVERED
	 * 4. 현재 시각이 상품 확인 기한 이상이면 409 INSPECTION_PERIOD_ENDED
	 * 5. 환불 요청이 이미 있으면 409 REFUND_ALREADY_REQUESTED
	 * 6. PENDING 환불 요청(응답 기한 = 접수 + 48시간) 생성과 거래 ON_HOLD 변경을 한 트랜잭션으로 반영
	 * 보류 중에도 실제 당사자 대화는 쓰기 가능이므로 대화 이벤트는 보내지 않는다.
	 */
	@Override
	@Transactional
	public OrderDetailResponse requestRefund(String memberId, Long orderId, RefundRequestCreateRequest request) {
		PurchaseOrder order = orderAccess.requireBuyer(memberId, orderId);
		if (order.getTradeStatus() == TradeStatus.ON_HOLD) {
			throw new BusinessException(ErrorCode.REFUND_ALREADY_REQUESTED);
		}
		if (order.getTradeStatus().isFinal()) {
			throw new BusinessException(ErrorCode.TRADE_ALREADY_FINALIZED);
		}
		if (order.getShippingStatus() != ShippingStatus.DELIVERED) {
			throw new BusinessException(ErrorCode.ORDER_NOT_DELIVERED);
		}
		Instant now = clock.instant();
		if (!now.isBefore(order.getInspectionDeadlineAt())) {
			throw new BusinessException(ErrorCode.INSPECTION_PERIOD_ENDED);
		}
		if (refundRequestRepository.existsByOrderId(orderId)) {
			throw new BusinessException(ErrorCode.REFUND_ALREADY_REQUESTED);
		}
		refundRequestRepository.save(RefundRequest.submit(orderId, memberId, request.reasonCode(), request.detail(),
				now, timeRules.fortyEightHoursAfter(now), request.requestId()));
		TradeStatus before = order.getTradeStatus();
		order.hold();
		log.info("환불 요청 접수 orderId={} memberId={} 거래 {} -> {}", orderId, memberId, before,
				order.getTradeStatus());
		return assembler.detail(order, memberId);
	}

	/**
	 * 처리 순서(설계 명세서 6.18절, 환불 정책은 요구사항 명세서 CH-001)
	 * 1. 주문이 없으면 404, 판매자가 아니면 403 NOT_SELLER
	 * 2. 환불 요청이 없으면 404 REFUND_REQUEST_NOT_FOUND
	 * 3. 이미 판단·자동 승인된 요청이면 409 REFUND_ALREADY_DECIDED, 주문이 보류 중이 아니면 409 ORDER_NOT_ON_HOLD
	 * 4. 현재 시각이 응답 기한 이상이면 409 REFUND_RESPONSE_DEADLINE_PASSED
	 * 5. 동의: 요청 APPROVED 와 공통 환불 처리(반품 없는 전액 환불)
	 * 6. 거절: 요청 REJECTED 와 공통 완료 처리(REFUND_REJECTED, 판매대금 지급)
	 */
	@Override
	@Transactional
	public OrderDetailResponse decide(String memberId, Long orderId, boolean approve) {
		PurchaseOrder order = orderAccess.requireSeller(memberId, orderId);
		RefundRequest refund = refundRequestRepository.findFirstByOrderIdOrderByRefundRequestIdAsc(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
		if (refund.getDecision() != RefundDecision.PENDING) {
			throw new BusinessException(ErrorCode.REFUND_ALREADY_DECIDED);
		}
		if (order.getTradeStatus() != TradeStatus.ON_HOLD) {
			throw new BusinessException(ErrorCode.ORDER_NOT_ON_HOLD);
		}
		Instant now = clock.instant();
		if (!now.isBefore(refund.getResponseDeadlineAt())) {
			throw new BusinessException(ErrorCode.REFUND_RESPONSE_DEADLINE_PASSED);
		}
		if (approve) {
			refund.decide(RefundDecision.APPROVED, now);
			log.info("환불 판단 orderId={} memberId={} 요청 PENDING -> {}", orderId, memberId, refund.getDecision());
			refund(order, now, memberId);
		} else {
			refund.decide(RefundDecision.REJECTED, now);
			log.info("환불 판단 orderId={} memberId={} 요청 PENDING -> {}", orderId, memberId, refund.getDecision());
			completionService.complete(order, CompletionCause.REFUND_REJECTED, now, memberId);
		}
		return assembler.detail(order, memberId);
	}

	/**
	 * 처리 순서(설계 명세서 6.18절 무응답 자동 환불)
	 * 1. 응답 대기, 응답 기한이 now 이하, 주문이 보류 중인 환불 요청을 조회한다
	 * 2. 요청마다 트랜잭션 하나에서 조건을 다시 확인하고 AUTO_APPROVED 로 바꾼 뒤 공통 환불 처리를 실행한다
	 */
	@Override
	public int autoApproveExpired(Instant now) {
		List<Long> targetIds = refundRequestRepository.findExpiredPendingIds(now);
		int processed = 0;
		for (Long refundRequestId : targetIds) {
			Boolean done = transactionTemplate.execute(status -> autoApproveIfExpired(refundRequestId, now));
			if (Boolean.TRUE.equals(done)) {
				processed++;
			}
		}
		log.info("환불 무응답 자동 환불 실행 now={} 대상={} 처리={}", now, targetIds.size(), processed);
		return processed;
	}

	private boolean autoApproveIfExpired(Long refundRequestId, Instant now) {
		RefundRequest refund = refundRequestRepository.findById(refundRequestId).orElse(null);
		if (refund == null || refund.getDecision() != RefundDecision.PENDING
				|| now.isBefore(refund.getResponseDeadlineAt())) {
			return false;
		}
		PurchaseOrder order = orderRepository.findById(refund.getOrderId()).orElse(null);
		if (order == null || order.getTradeStatus() != TradeStatus.ON_HOLD) {
			return false;
		}
		refund.decide(RefundDecision.AUTO_APPROVED, now);
		log.info("환불 자동 승인 orderId={} memberId=SYSTEM 요청 PENDING -> {}", order.getOrderId(),
				refund.getDecision());
		refund(order, now, null);
		return true;
	}

	/**
	 * 공통 환불 처리. 거래 REFUNDED 와 종료 시각 기록, 구매자 잔액에 결제 금액 전액 반환과 REFUND 내역을
	 * 호출한 쪽의 트랜잭션 안에서 함께 반영하고, 커밋 뒤 당사자 대화에 CONVERSATION_STATE 를 보낸다.
	 */
	private void refund(PurchaseOrder order, Instant now, String actorId) {
		TradeStatus before = order.getTradeStatus();
		order.refund(now);
		walletService.credit(order.getBuyerId(), TransactionType.REFUND, order.getPaidAmount(), order.getOrderId(),
				null, now);
		log.info("환불 완료 orderId={} memberId={} 거래 {} -> {}", order.getOrderId(),
				actorId == null ? "SYSTEM" : actorId, before, order.getTradeStatus());
		conversationNotifier.notifyPartyConversation(order.getProductId(), order.getBuyerId(), actorId);
	}
}
