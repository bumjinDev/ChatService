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
import com.chatservice.marketplace.conversation.ConversationNotifier;
import com.chatservice.marketplace.wallet.IWalletService;
import com.chatservice.marketplace.wallet.TransactionType;

/** 정상 완료와 판매대금 지급(F-015, F-016). 판매자의 환불 거절(F-018)도 공통 완료 처리를 쓴다. */
@Service
public class TradeCompletionService implements ITradeCompletionService {

	private static final Logger log = LoggerFactory.getLogger(TradeCompletionService.class);

	private final OrderAccess orderAccess;
	private final PurchaseOrderRepository orderRepository;
	private final RefundRequestRepository refundRequestRepository;
	private final TransactionTemplate transactionTemplate;
	private final IWalletService walletService;
	private final ConversationNotifier conversationNotifier;
	private final OrderDetailAssembler assembler;
	private final Clock clock;

	public TradeCompletionService(OrderAccess orderAccess, PurchaseOrderRepository orderRepository,
			RefundRequestRepository refundRequestRepository, PlatformTransactionManager transactionManager,
			IWalletService walletService, ConversationNotifier conversationNotifier, OrderDetailAssembler assembler,
			Clock clock) {
		this.orderAccess = orderAccess;
		this.orderRepository = orderRepository;
		this.refundRequestRepository = refundRequestRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.walletService = walletService;
		this.conversationNotifier = conversationNotifier;
		this.assembler = assembler;
		this.clock = clock;
	}

	/**
	 * 처리 순서(설계 명세서 6.15절)
	 * 1. 주문이 없으면 404, 구매자가 아니면 403 NOT_BUYER
	 * 2. 보류 중이면 409 ORDER_ON_HOLD, 최종 상태이면 409 TRADE_ALREADY_FINALIZED
	 * 3. 배송 완료가 아니면 409 ORDER_NOT_DELIVERED
	 * 4. 현재 시각이 상품 확인 기한 이상이면 409 INSPECTION_PERIOD_ENDED(정확히 48시간부터 거절)
	 * 5. 공통 완료 처리(BUYER_CONFIRMED)
	 */
	@Override
	@Transactional
	public OrderDetailResponse confirmReceipt(String memberId, Long orderId) {
		PurchaseOrder order = orderAccess.requireBuyer(memberId, orderId);
		if (order.getTradeStatus() == TradeStatus.ON_HOLD) {
			throw new BusinessException(ErrorCode.ORDER_ON_HOLD);
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
		complete(order, CompletionCause.BUYER_CONFIRMED, now, memberId);
		return assembler.detail(order, memberId);
	}

	/**
	 * 처리 순서(설계 명세서 6.16절)
	 * 1. 진행 중, 배송 완료, 상품 확인 기한이 now 이하인 주문을 조회한다
	 * 2. 주문마다 트랜잭션 하나에서 조건을 다시 확인하고 환불 요청이 없을 때만 공통 완료 처리(AUTO_EXPIRED)를 실행한다.
	 *    환불이 접수된 주문은 보류(ON_HOLD)라 조회 조건에서 빠지지만 한 번 더 확인한다
	 */
	@Override
	public int completeExpiredInspections(Instant now) {
		List<Long> targetIds = orderRepository.findExpiredInspectionIds(now);
		int processed = 0;
		for (Long orderId : targetIds) {
			Boolean done = transactionTemplate.execute(status -> completeIfExpired(orderId, now));
			if (Boolean.TRUE.equals(done)) {
				processed++;
			}
		}
		log.info("상품 확인 기간 만료 자동 완료 실행 now={} 대상={} 처리={}", now, targetIds.size(), processed);
		return processed;
	}

	private boolean completeIfExpired(Long orderId, Instant now) {
		PurchaseOrder order = orderRepository.findById(orderId).orElse(null);
		if (order == null
				|| order.getTradeStatus() != TradeStatus.IN_PROGRESS
				|| order.getShippingStatus() != ShippingStatus.DELIVERED
				|| order.getInspectionDeadlineAt() == null
				|| now.isBefore(order.getInspectionDeadlineAt())
				|| refundRequestRepository.existsByOrderId(orderId)) {
			return false;
		}
		complete(order, CompletionCause.AUTO_EXPIRED, now, null);
		return true;
	}

	/**
	 * 공통 완료 처리. 거래 COMPLETED 와 완료 사유·종료 시각 기록, 판매자 잔액에 결제 금액 지급과 SALE_PAYOUT 내역을
	 * 호출한 쪽의 트랜잭션 안에서 함께 반영하고, 커밋 뒤 당사자 대화에 CONVERSATION_STATE 를 보낸다.
	 * actorId 가 null 이면 시스템 처리로 보고 두 참여자 모두에게 보낸다.
	 */
	@Override
	@Transactional
	public void complete(PurchaseOrder order, CompletionCause cause, Instant now, String actorId) {
		TradeStatus before = order.getTradeStatus();
		order.complete(cause, now);
		walletService.credit(order.getSellerId(), TransactionType.SALE_PAYOUT, order.getPaidAmount(),
				order.getOrderId(), null, now);
		log.info("거래 정상 완료 orderId={} memberId={} cause={} 거래 {} -> {}", order.getOrderId(),
				actorId == null ? "SYSTEM" : actorId, cause, before, order.getTradeStatus());
		conversationNotifier.notifyPartyConversation(order.getProductId(), order.getBuyerId(), actorId);
	}
}
