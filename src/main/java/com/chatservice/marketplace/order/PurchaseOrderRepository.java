package com.chatservice.marketplace.order;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatservice.marketplace.order.domain.OrderEnums.ShippingStatus;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.PurchaseOrder;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    /* 한 대화의 실제 당사자 주문: 상품·구매자·판매자가 모두 일치. 다른 구매자의 주문은 포함되지 않는다(BR-007, BR-008). */
    List<PurchaseOrder> findByProductIdAndBuyerIdAndSellerIdOrderByOrderIdDesc(Long productId, String buyerId, String sellerId);

    /* 구매 목록·판매 목록: 구매 확정 시각 내림차순(설계 5.2.14) */
    List<PurchaseOrder> findByBuyerIdOrderByConfirmedAtDescOrderIdDesc(String buyerId);

    List<PurchaseOrder> findBySellerIdOrderByConfirmedAtDescOrderIdDesc(String sellerId);

    /**
     * 발송 기한이 지난 주문의 ID 목록을 조회한다.
     *
     * 조건: 거래 상태가 {@code tradeStatus}, 배송 상태가 {@code shippingStatus}이고 발송 기한이 {@code now} 이하인 주문.
     * 미발송 자동 취소는 {@code IN_PROGRESS}, {@code WAITING_SHIPMENT}를 전달한다.
     * 발송 정보(SHIPMENT) 행의 존재 여부는 조회 조건에 없다. 호출하는 쪽이 주문마다 다시 확인한다.
     * 엔티티가 아니라 ID만 조회하며, 잠금을 걸지 않는다. 주문 ID 오름차순으로 반환한다.
     *
     * @param tradeStatus    조회할 거래 상태
     * @param shippingStatus 조회할 배송 상태
     * @param now            기준 시각. 발송 기한이 이 값 이하인 주문을 조회한다.
     * @return 조건에 맞는 주문 ID 목록. 없으면 빈 목록
     */
    @Query("select o.orderId from PurchaseOrder o"
            + " where o.tradeStatus = :tradeStatus and o.shippingStatus = :shippingStatus and o.shipDeadlineAt <= :now"
            + " order by o.orderId")
    List<Long> findIdsByDeadlinePassed(@Param("tradeStatus") TradeStatus tradeStatus,
                                       @Param("shippingStatus") ShippingStatus shippingStatus,
                                       @Param("now") Instant now);

    /**
     * 모의 배송 완료 예정 시각이 지난 주문의 ID 목록을 조회한다.
     *
     * 조건: 거래 상태가 {@code tradeStatus}, 배송 상태가 {@code shippingStatus}이고, 배송 완료 시각이 null이며, 발송 정보의 모의 배송 완료 예정 시각이 {@code now} 이하인 주문.
     * 모의 배송 완료는 {@code IN_PROGRESS}, {@code SHIPPING}을 전달한다.
     * PURCHASE_ORDER와 SHIPMENT를 주문 ID로 내부 조인하므로, 발송 정보가 없는 주문은 조회되지 않는다.
     * 배송 완료 시각이 있는 주문을 제외하므로, 반복 실행해도 이미 기록한 배송 완료 시각과 상품 확인 기한을 다시 쓰지 않는다.
     * 엔티티가 아니라 ID만 조회하며, 잠금을 걸지 않는다. 주문 ID 오름차순으로 반환한다.
     *
     * @param tradeStatus    조회할 거래 상태
     * @param shippingStatus 조회할 배송 상태
     * @param now            기준 시각. 모의 배송 완료 예정 시각이 이 값 이하인 주문을 조회한다.
     * @return 조건에 맞는 주문 ID 목록. 없으면 빈 목록
     */
    @Query("select o.orderId from PurchaseOrder o, Shipment s"
            + " where s.orderId = o.orderId and o.shippingStatus = :shippingStatus and o.tradeStatus = :tradeStatus"
            + " and o.deliveredAt is null and s.deliveryDueAt <= :now"
            + " order by o.orderId")
    List<Long> findIdsByDeliveryDue(@Param("tradeStatus") TradeStatus tradeStatus,
                                    @Param("shippingStatus") ShippingStatus shippingStatus,
                                    @Param("now") Instant now);

    /**
     * 상품 확인 기한이 지난 주문의 ID 목록을 조회한다.
     *
     * 조건: 거래 상태가 {@code tradeStatus}, 배송 상태가 {@code shippingStatus}이고 상품 확인 기한이 {@code now} 이하인 주문.
     * 확인 기간 만료 자동 완료는 {@code IN_PROGRESS}, {@code DELIVERED}를 전달한다.
     * 기한 안에 환불 요청이 접수된 주문은 보류(ON_HOLD)이므로 거래 상태 조건에서 제외된다.
     * 환불 요청(REFUND_REQUEST) 행의 존재 여부는 조회 조건에 없다. 호출하는 쪽이 주문마다 다시 확인한다.
     * 엔티티가 아니라 ID만 조회하며, 잠금을 걸지 않는다. 주문 ID 오름차순으로 반환한다.
     *
     * @param tradeStatus    조회할 거래 상태
     * @param shippingStatus 조회할 배송 상태
     * @param now            기준 시각. 상품 확인 기한이 이 값 이하인 주문을 조회한다.
     * @return 조건에 맞는 주문 ID 목록. 없으면 빈 목록
     */
    @Query("select o.orderId from PurchaseOrder o"
            + " where o.tradeStatus = :tradeStatus and o.shippingStatus = :shippingStatus and o.inspectionDeadlineAt <= :now"
            + " order by o.orderId")
    List<Long> findIdsByInspectionExpired(@Param("tradeStatus") TradeStatus tradeStatus,
                                          @Param("shippingStatus") ShippingStatus shippingStatus,
                                          @Param("now") Instant now);
}
