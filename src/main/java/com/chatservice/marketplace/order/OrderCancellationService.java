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
import com.chatservice.marketplace.common.MemberRole;
import com.chatservice.marketplace.conversation.ConversationNotifier;
import com.chatservice.marketplace.wallet.IWalletService;
import com.chatservice.marketplace.wallet.TransactionType;

/** 발송 전 주문 취소(F-012)와 미발송 자동 취소(F-013). */
@Service
public class OrderCancellationService implements IOrderCancellationService {

	private static final Logger log = LoggerFactory.getLogger(OrderCancellationService.class);

	/** 자동 취소의 취소 사유 고정 문자열 */
	public static final String AUTO_CANCEL_REASON = "SHIPMENT_DEADLINE_EXPIRED";

	private final OrderAccess orderAccess;
	private final PurchaseOrderRepository orderRepository;
	private final ShipmentRepository shipmentRepository;
	private final TransactionTemplate transactionTemplate;
	private final IWalletService walletService;
	private final ConversationNotifier conversationNotifier;
	private final OrderDetailAssembler assembler;
	private final Clock clock;

	public OrderCancellationService(OrderAccess orderAccess, PurchaseOrderRepository orderRepository,
			ShipmentRepository shipmentRepository, PlatformTransactionManager transactionManager,
			IWalletService walletService, ConversationNotifier conversationNotifier, OrderDetailAssembler assembler,
			Clock clock) {
		this.orderAccess = orderAccess;
		this.orderRepository = orderRepository;
		this.shipmentRepository = shipmentRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.walletService = walletService;
		this.conversationNotifier = conversationNotifier;
		this.assembler = assembler;
		this.clock = clock;
	}

	/**
	 * 처리 순서(설계 명세서 6.12절)
	 * 1. 주문이 없으면 404, 당사자가 아니면 403 NOT_TRADE_PARTY
	 * 2. 이미 최종 상태이면 409 TRADE_ALREADY_FINALIZED
	 * 3. 발송 대기가 아니면 409 ORDER_ALREADY_SHIPPED(보류 중 주문도 여기서 거절된다)
	 * 4. 공통 취소 처리. 상품은 판매 종료로 남는다
	 */
	@Override
	@Transactional
	public OrderDetailResponse cancelByParty(String memberId, Long orderId, OrderCancelRequest request) {
		PurchaseOrder order = orderAccess.requireParty(memberId, orderId);
		if (order.getTradeStatus().isFinal()) {
			throw new BusinessException(ErrorCode.TRADE_ALREADY_FINALIZED);
		}
		if (order.getShippingStatus() != ShippingStatus.WAITING_SHIPMENT) {
			throw new BusinessException(ErrorCode.ORDER_ALREADY_SHIPPED);
		}
		CancelledBy cancelledBy = order.roleOf(memberId) == MemberRole.BUYER ? CancelledBy.BUYER : CancelledBy.SELLER;
		cancel(order, cancelledBy, request.reason(), clock.instant(), memberId);
		return assembler.detail(order, memberId);
	}

	/**
	 * 처리 순서(설계 명세서 6.13절)
	 * 1. 진행 중, 발송 대기, 발송 기한이 now 이하인 주문을 조회한다
	 * 2. 주문마다 트랜잭션 하나에서 상태를 다시 읽고 발송 정보가 없는지 확인한 뒤
	 *    공통 취소 처리를 취소 주체 SYSTEM, 사유 SHIPMENT_DEADLINE_EXPIRED 로 실행한다
	 * 3. 대상 건수와 처리 건수를 로그에 남긴다
	 */
	@Override
	public int cancelExpiredUnshipped(Instant now) {
		List<Long> targetIds = orderRepository.findExpiredUnshippedIds(now);
		int processed = 0;
		for (Long orderId : targetIds) {
			Boolean done = transactionTemplate.execute(status -> cancelIfStillExpired(orderId, now));
			if (Boolean.TRUE.equals(done)) {
				processed++;
			}
		}
		log.info("미발송 자동 취소 실행 now={} 대상={} 처리={}", now, targetIds.size(), processed);
		return processed;
	}

	private boolean cancelIfStillExpired(Long orderId, Instant now) {
		PurchaseOrder order = orderRepository.findById(orderId).orElse(null);
		if (order == null
				|| order.getTradeStatus() != TradeStatus.IN_PROGRESS
				|| order.getShippingStatus() != ShippingStatus.WAITING_SHIPMENT
				|| now.isBefore(order.getShipDeadlineAt())
				|| shipmentRepository.existsByOrderId(orderId)) {
			return false;
		}
		cancel(order, CancelledBy.SYSTEM, AUTO_CANCEL_REASON, now, null);
		return true;
	}

	/**
	 * 공통 취소 처리. 주문 상태 CANCELLED 와 취소 정보 기록, 구매자 잔액 전액 반환과 CANCEL_REFUND 내역을
	 * 호출한 쪽의 트랜잭션 안에서 함께 반영하고, 커밋 뒤 당사자 대화에 CONVERSATION_STATE 를 보낸다.
	 */
	void cancel(PurchaseOrder order, CancelledBy cancelledBy, String reason, Instant now, String actorId) {
		TradeStatus before = order.getTradeStatus();
		order.cancel(cancelledBy, reason, now);
		walletService.credit(order.getBuyerId(), TransactionType.CANCEL_REFUND, order.getPaidAmount(),
				order.getOrderId(), null, now);
		log.info("주문 취소 orderId={} memberId={} cancelledBy={} 거래 {} -> {}", order.getOrderId(), actorId,
				cancelledBy, before, order.getTradeStatus());
		conversationNotifier.notifyPartyConversation(order.getProductId(), order.getBuyerId(), actorId);
	}
}
