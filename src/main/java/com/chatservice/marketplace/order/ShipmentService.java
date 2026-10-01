package com.chatservice.marketplace.order;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;

/** 발송 등록(F-011)과 모의 배송 완료(F-014). */
@Service
public class ShipmentService implements IShipmentService {

	private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

	private final OrderAccess orderAccess;
	private final ShipmentRepository shipmentRepository;
	private final OrderDetailAssembler assembler;
	private final Clock clock;
	private final Duration mockDeliveryDuration;

	public ShipmentService(OrderAccess orderAccess, ShipmentRepository shipmentRepository,
			OrderDetailAssembler assembler, Clock clock,
			@Value("${marketplace.mock-delivery.duration}") Duration mockDeliveryDuration) {
		this.orderAccess = orderAccess;
		this.shipmentRepository = shipmentRepository;
		this.assembler = assembler;
		this.clock = clock;
		this.mockDeliveryDuration = mockDeliveryDuration;
	}

	/**
	 * 처리 순서(설계 명세서 6.11절)
	 * 1. 주문이 없으면 404, 판매자가 아니면 403 NOT_SELLER
	 * 2. 정상 완료·환불 완료·취소 완료이면 409 ORDER_NOT_IN_PROGRESS
	 * 3. 발송 정보가 이미 있으면 409 ALREADY_SHIPPED(원래 정보 유지)
	 * 4. 현재 시각이 발송 기한 이상이면 409 SHIPMENT_DEADLINE_PASSED(자동 취소 전이어도 거절)
	 * 5. SHIPMENT 생성과 주문 배송 상태 SHIPPING 변경을 한 트랜잭션으로 반영
	 */
	@Override
	@Transactional
	public OrderDetailResponse registerShipment(String memberId, Long orderId, ShipmentRegisterRequest request) {
		PurchaseOrder order = orderAccess.requireSeller(memberId, orderId);
		if (order.getTradeStatus().isFinal()) {
			throw new BusinessException(ErrorCode.ORDER_NOT_IN_PROGRESS);
		}
		if (shipmentRepository.existsByOrderId(orderId)) {
			throw new BusinessException(ErrorCode.ALREADY_SHIPPED);
		}
		Instant now = clock.instant();
		if (!now.isBefore(order.getShipDeadlineAt())) {
			throw new BusinessException(ErrorCode.SHIPMENT_DEADLINE_PASSED);
		}
		shipmentRepository.save(Shipment.register(orderId, request.carrierName(), request.trackingNumber(), now,
				now.plus(mockDeliveryDuration), request.requestId()));
		ShippingStatus before = order.getShippingStatus();
		order.markShipping();
		log.info("발송 등록 orderId={} memberId={} 배송 {} -> {}", orderId, memberId, before, order.getShippingStatus());
		return assembler.detail(order, memberId);
	}
}
