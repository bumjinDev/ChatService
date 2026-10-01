package com.chatservice.marketplace.order;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.common.TimeRules;

/** 발송 등록(F-011)과 모의 배송 완료(F-014). */
@Service
public class ShipmentService implements IShipmentService {

	private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

	private final OrderAccess orderAccess;
	private final PurchaseOrderRepository orderRepository;
	private final ShipmentRepository shipmentRepository;
	private final TimeRules timeRules;
	private final TransactionTemplate transactionTemplate;
	private final OrderDetailAssembler assembler;
	private final Clock clock;
	private final Duration mockDeliveryDuration;

	public ShipmentService(OrderAccess orderAccess, PurchaseOrderRepository orderRepository,
			ShipmentRepository shipmentRepository, TimeRules timeRules, PlatformTransactionManager transactionManager,
			OrderDetailAssembler assembler, Clock clock,
			@Value("${marketplace.mock-delivery.duration}") Duration mockDeliveryDuration) {
		this.orderAccess = orderAccess;
		this.orderRepository = orderRepository;
		this.shipmentRepository = shipmentRepository;
		this.timeRules = timeRules;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
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

	/**
	 * 처리 순서(설계 명세서 6.14절)
	 * 1. 배송 중, 진행 중, 모의 배송 완료 예정 시각이 now 이하, 배송 완료 시각이 없는 주문을 조회한다
	 * 2. 주문마다 트랜잭션 하나에서 조건을 다시 확인하고 DELIVERED 로 바꾼 뒤
	 *    배송 완료 안내 시점(now)과 상품 확인 기한(안내 시점 + 48시간)을 기록한다
	 * 배송 완료만으로 거래를 완료하거나 판매대금을 지급하지 않으며, 쓰기 가능 여부가 바뀌지 않으므로 이벤트도 보내지 않는다.
	 */
	@Override
	public int completeDueDeliveries(Instant now) {
		List<Long> targetIds = orderRepository.findDueDeliveryIds(now);
		int processed = 0;
		for (Long orderId : targetIds) {
			Boolean done = transactionTemplate.execute(status -> deliverIfDue(orderId, now));
			if (Boolean.TRUE.equals(done)) {
				processed++;
			}
		}
		log.info("모의 배송 완료 실행 now={} 대상={} 처리={}", now, targetIds.size(), processed);
		return processed;
	}

	private boolean deliverIfDue(Long orderId, Instant now) {
		PurchaseOrder order = orderRepository.findById(orderId).orElse(null);
		if (order == null
				|| order.getShippingStatus() != ShippingStatus.SHIPPING
				|| order.getTradeStatus() != TradeStatus.IN_PROGRESS
				|| order.getDeliveredAt() != null) {
			return false;
		}
		Shipment shipment = shipmentRepository.findFirstByOrderIdOrderByShipmentIdAsc(orderId).orElse(null);
		if (shipment == null || now.isBefore(shipment.getDeliveryDueAt())) {
			return false;
		}
		ShippingStatus before = order.getShippingStatus();
		order.markDelivered(now, timeRules.fortyEightHoursAfter(now));
		log.info("모의 배송 완료 orderId={} memberId=SYSTEM 배송 {} -> {} inspectionDeadlineAt={}", orderId, before,
				order.getShippingStatus(), order.getInspectionDeadlineAt());
		return true;
	}
}
