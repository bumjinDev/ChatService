package com.chatservice.marketplace.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

	/** 판매 종료된 상품 하나에 주문은 하나다. 가장 먼저 만들어진 주문을 돌려준다. */
	Optional<PurchaseOrder> findFirstByProductIdOrderByOrderIdAsc(Long productId);

	List<PurchaseOrder> findByBuyerIdOrderByConfirmedAtDescOrderIdDesc(String buyerId);

	List<PurchaseOrder> findBySellerIdOrderByConfirmedAtDescOrderIdDesc(String sellerId);

	/** 미발송 자동 취소 대상: 진행 중, 발송 대기, 발송 기한이 now 이하. */
	@Query("SELECT o.orderId FROM PurchaseOrder o WHERE o.tradeStatus = com.chatservice.marketplace.order.TradeStatus.IN_PROGRESS "
			+ "AND o.shippingStatus = com.chatservice.marketplace.order.ShippingStatus.WAITING_SHIPMENT "
			+ "AND o.shipDeadlineAt <= :now ORDER BY o.orderId")
	List<Long> findExpiredUnshippedIds(@Param("now") Instant now);

	/** 모의 배송 완료 대상: 배송 중, 진행 중, 배송 완료 예정 시각이 now 이하, 배송 완료 시각 없음. */
	@Query("SELECT o.orderId FROM PurchaseOrder o, Shipment s WHERE s.orderId = o.orderId "
			+ "AND o.shippingStatus = com.chatservice.marketplace.order.ShippingStatus.SHIPPING "
			+ "AND o.tradeStatus = com.chatservice.marketplace.order.TradeStatus.IN_PROGRESS "
			+ "AND s.deliveryDueAt <= :now AND o.deliveredAt IS NULL ORDER BY o.orderId")
	List<Long> findDueDeliveryIds(@Param("now") Instant now);

	/** 확인 기간 만료 자동 완료 대상: 진행 중, 배송 완료, 상품 확인 기한이 now 이하. */
	@Query("SELECT o.orderId FROM PurchaseOrder o WHERE o.tradeStatus = com.chatservice.marketplace.order.TradeStatus.IN_PROGRESS "
			+ "AND o.shippingStatus = com.chatservice.marketplace.order.ShippingStatus.DELIVERED "
			+ "AND o.inspectionDeadlineAt <= :now ORDER BY o.orderId")
	List<Long> findExpiredInspectionIds(@Param("now") Instant now);
}
