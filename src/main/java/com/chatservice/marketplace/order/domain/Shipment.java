package com.chatservice.marketplace.order.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자가 주문에 처음 등록한 발송 정보를 나타내는 엔티티. SHIPMENT 테이블에 매핑한다.
 *
 * 이 테이블에 주문의 행이 있으면 그 주문은 발송된 것으로 판단한다.
 * - 미발송 자동 취소와 발송 등록은 {@code ShipmentRepository.existsByOrderId}로 행의 존재 여부를 확인한다.
 * - 모의 배송 완료는 {@code ShipmentRepository.findByOrderId}로 행을 조회해 {@link #deliveryDueAt}을 확인한다.
 *
 * 발송 정보는 주문당 한 번만 등록하며, 등록 뒤 수정·재등록·삭제하는 기능이 없다.
 * - ORDER_ID 컬럼에는 UNIQUE 제약이 없다. 주문당 한 행은 {@code ShipmentService.register}가 저장 전에 {@code existsByOrderId}로 확인해 유지한다.
 * - 배송 완료 시각은 이 엔티티가 아니라 {@link PurchaseOrder}의 {@code deliveredAt}에 저장한다.
 *
 * 택배사명과 운송장 번호는 요청 DTO에서 공백 여부와 길이만 검증한다. 실제 택배사 목록과 운송장 번호 형식은 검증하지 않는다.
 */
@Entity
@Table(name = "SHIPMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

    /** 발송 정보 ID. DB의 IDENTITY 컬럼이 생성한다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SHIPMENT_ID")
    private Long shipmentId;

    /** 발송한 주문의 ID. PURCHASE_ORDER.ORDER_ID를 참조하는 외래 키다. */
    @Column(name = "ORDER_ID", nullable = false)
    private Long orderId;

    /** 택배사명. */
    @Column(name = "CARRIER_NAME", nullable = false)
    private String carrierName;

    /** 운송장 번호. */
    @Column(name = "TRACKING_NUMBER", nullable = false)
    private String trackingNumber;

    /** 발송 등록 시각. 모의 배송이 시작되는 시각이다. */
    @Column(name = "SHIPPED_AT", nullable = false)
    private Instant shippedAt;

    /**
     * 모의 배송 완료 예정 시각. 발송 등록 시각에 모의 배송 기간({@code marketplace.mock-delivery.duration})을 더한 값이다.
     * 현재 시각이 이 값 이상이면 모의 배송 완료 대상이 된다.
     */
    @Column(name = "DELIVERY_DUE_AT", nullable = false)
    private Instant deliveryDueAt;

    /** 발송 등록 요청에 클라이언트가 넣은 요청 식별자. 값이 없으면 null이다. */
    @Column(name = "REQUEST_ID")
    private String requestId;

    /**
     * 새 발송 정보 엔티티를 생성한다.
     *
     * 엔티티 객체만 생성하며, 저장은 호출하는 쪽이 repository로 한다.
     * 주문의 배송 상태 변경({@link PurchaseOrder#markShipped})과 발송 기한·중복 등록 확인도 호출하는 쪽이 한다.
     * 호출하는 쪽: {@code ShipmentService.register}
     *
     * @param orderId        발송한 주문의 ID
     * @param carrierName    택배사명
     * @param trackingNumber 운송장 번호
     * @param shippedAt      발송 등록 시각
     * @param deliveryDueAt  모의 배송 완료 예정 시각. 호출하는 쪽이 {@code shippedAt}에 모의 배송 기간을 더해 계산한 값
     * @param requestId      요청 식별자. 없으면 null
     * @return 아직 저장하지 않은 발송 정보 엔티티. {@code shipmentId}는 저장할 때 생성된다.
     */
    public static Shipment register(Long orderId, String carrierName, String trackingNumber,
                                    Instant shippedAt, Instant deliveryDueAt, String requestId) {
        Shipment shipment = new Shipment();
        shipment.orderId = orderId;
        shipment.carrierName = carrierName;
        shipment.trackingNumber = trackingNumber;
        shipment.shippedAt = shippedAt;
        shipment.deliveryDueAt = deliveryDueAt;
        shipment.requestId = requestId;
        return shipment;
    }
}
