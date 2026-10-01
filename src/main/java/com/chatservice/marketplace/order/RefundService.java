package com.chatservice.marketplace.order;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.common.TimeRules;

/** 환불 요청과 거래 보류(F-017), 판매자 환불 응답과 무응답 자동 환불(F-018). */
@Service
public class RefundService implements IRefundService {

	private static final Logger log = LoggerFactory.getLogger(RefundService.class);

	private final OrderAccess orderAccess;
	private final RefundRequestRepository refundRequestRepository;
	private final OrderDetailAssembler assembler;
	private final TimeRules timeRules;
	private final Clock clock;

	public RefundService(OrderAccess orderAccess, RefundRequestRepository refundRequestRepository,
			OrderDetailAssembler assembler, TimeRules timeRules, Clock clock) {
		this.orderAccess = orderAccess;
		this.refundRequestRepository = refundRequestRepository;
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
}
