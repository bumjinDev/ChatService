package com.chatservice.marketplace.order.domain;

import java.time.Instant;

import com.chatservice.marketplace.order.domain.OrderEnums.CancelledBy;
import com.chatservice.marketplace.order.domain.OrderEnums.CompletionCause;
import com.chatservice.marketplace.order.domain.OrderEnums.PriceSource;
import com.chatservice.marketplace.order.domain.OrderEnums.ShippingStatus;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제가 성공해 구매가 확정된 주문을 나타내는 엔티티. PURCHASE_ORDER 테이블에 매핑한다.
 *
 * 테이블 이름이 ORDER가 아닌 이유는 ORDER가 Oracle 예약어이기 때문이다.
 *
 * 주문 상태는 두 컬럼에 나누어 저장한다.
 * - {@link #shippingStatus}: 배송 진행 상태. {@code WAITING_SHIPMENT → SHIPPING → DELIVERED} 순서로만 바뀐다.
 * - {@link #tradeStatus}: 거래 결과 상태. {@code IN_PROGRESS}는 거래 결과가 정해지지 않았고 환불 요청도 없는 상태다.
 *
 * 상태를 설정하는 메서드와 실행 뒤의 (거래 상태, 배송 상태) 조합:
 * 1. {@link #place}: 주문 생성. ({@code IN_PROGRESS}, {@code WAITING_SHIPMENT})
 * 2. {@link #markShipped}: 발송 등록. ({@code IN_PROGRESS}, {@code SHIPPING})
 * 3. {@link #markDelivered}: 모의 배송 완료. ({@code IN_PROGRESS}, {@code DELIVERED})
 * 4. {@link #cancel}: 발송 전 취소. ({@code CANCELLED}, {@code WAITING_SHIPMENT})
 * 5. {@link #hold}: 환불 요청 접수. ({@code ON_HOLD}, {@code DELIVERED})
 * 6. {@link #complete}: 정상 완료. ({@code COMPLETED}, {@code DELIVERED})
 * 7. {@link #refund}: 환불 완료. ({@code REFUNDED}, {@code DELIVERED})
 * 이 일곱 메서드는 위의 일곱 가지 조합만 만든다.
 *
 * 상태 전환 메서드(2~7번)의 공통 동작:
 * - 엔티티 필드만 바꾼다. DB 반영은 flush 때 dirty checking으로 실행되는 UPDATE가 한다.
 * - 잔액 변경, 발송 정보·환불 요청 행 생성, 대화 상태 이벤트 전송은 호출하는 서비스가 한다.
 * - 업무 규칙(권한, 기한, 중복 요청)은 호출하는 서비스가 먼저 검사한다. {@link #requireState}는 허용되지 않은 상태 전환을 막는 마지막 검사다.
 * - 상태 검사는 현재 트랜잭션이 조회한 필드 값으로 한다. 조회 뒤에 다른 트랜잭션이 커밋한 변경은 검사에 반영되지 않는다.
 *
 * @see OrderEnums.TradeStatus
 * @see OrderEnums.ShippingStatus
 */
@Entity
@Table(name = "PURCHASE_ORDER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder {

    /** 주문 ID. DB의 IDENTITY 컬럼이 생성한다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_ID")
    private Long orderId;

    /** 구매한 상품의 ID. */
    @Column(name = "PRODUCT_ID", nullable = false)
    private Long productId;

    /** 구매자의 회원 ID. */
    @Column(name = "BUYER_ID", nullable = false)
    private String buyerId;

    /** 판매자의 회원 ID. 주문을 생성할 때 상품의 판매자 ID를 저장한다. */
    @Column(name = "SELLER_ID", nullable = false)
    private String sellerId;

    /** 주문 수량. */
    @Column(name = "QUANTITY", nullable = false)
    private long quantity;

    /** 주문에 적용한 단가. 단가의 출처는 {@link #priceSource}에 저장한다. */
    @Column(name = "UNIT_PRICE", nullable = false)
    private long unitPrice;

    /** 실제 총 결제 금액(단가 × 수량). 생성 뒤 변경하지 않으며, 취소 반환·환불 반환·판매대금 지급은 모두 이 금액 전액으로 처리한다. */
    @Column(name = "PAID_AMOUNT", nullable = false)
    private long paidAmount;

    /** 단가의 출처. 등록 가격이면 {@code LISTED}, 수락된 가격 제안의 금액이면 {@code AGREED}다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "PRICE_SOURCE", nullable = false)
    private PriceSource priceSource;

    /** 적용한 가격 제안의 ID. {@link #priceSource}가 {@code AGREED}일 때만 값이 있고, {@code LISTED}이면 null이다. */
    @Column(name = "OFFER_ID")
    private Long offerId;

    /** 수령인 이름. */
    @Column(name = "RECIPIENT_NAME", nullable = false)
    private String recipientName;

    /** 배송 주소. */
    @Column(name = "SHIPPING_ADDRESS", nullable = false)
    private String shippingAddress;

    /** 배송 진행 상태. 생성 시 {@code WAITING_SHIPMENT}이며 {@link #markShipped}와 {@link #markDelivered}가 변경한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "SHIPPING_STATUS", nullable = false)
    private ShippingStatus shippingStatus;

    /** 거래 결과 상태. 생성 시 {@code IN_PROGRESS}이며 {@link #cancel}, {@link #hold}, {@link #complete}, {@link #refund}가 변경한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "TRADE_STATUS", nullable = false)
    private TradeStatus tradeStatus;

    /** 정상 완료 사유. {@link #complete}가 저장하며, 정상 완료가 아닌 주문은 null이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "COMPLETION_CAUSE")
    private CompletionCause completionCause;

    /** 구매 확정 시각. 주문을 생성한 시각이며, 발송 기한은 이 시각으로 계산한다. */
    @Column(name = "CONFIRMED_AT", nullable = false)
    private Instant confirmedAt;

    /** 발송 기한. 이 시각부터는 발송 등록이 거절되고, 발송 정보가 없는 주문은 미발송 자동 취소 대상이 된다. */
    @Column(name = "SHIP_DEADLINE_AT", nullable = false)
    private Instant shipDeadlineAt;

    /** 최초 모의 배송 완료 시각. {@link #markDelivered}가 한 번만 저장하며 이후 변경하지 않는다. 배송 완료 전에는 null이다. */
    @Column(name = "DELIVERED_AT")
    private Instant deliveredAt;

    /** 상품 확인 기한. 배송 완료 시각에 48시간을 더한 값이며 {@link #markDelivered}가 저장한다. 배송 완료 전에는 null이다. */
    @Column(name = "INSPECTION_DEADLINE_AT")
    private Instant inspectionDeadlineAt;

    /** 거래 종료 시각. 정상 완료, 환불 완료, 취소 완료로 바뀐 시각이며 그 전에는 null이다. */
    @Column(name = "FINALIZED_AT")
    private Instant finalizedAt;

    /** 취소 주체. {@link #cancel}이 저장하며, 취소되지 않은 주문은 null이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "CANCELLED_BY")
    private CancelledBy cancelledBy;

    /** 취소 사유. 미발송 자동 취소는 {@code SHIPMENT_DEADLINE_EXPIRED}를 저장한다. 취소되지 않은 주문은 null이다. */
    @Column(name = "CANCEL_REASON")
    private String cancelReason;

    /** 결제 요청에 클라이언트가 넣은 요청 식별자. */
    @Column(name = "REQUEST_ID")
    private String requestId;

    /**
     * 결제가 성공했을 때 새 주문 엔티티를 생성한다.
     *
     * 거래 상태는 {@code IN_PROGRESS}, 배송 상태는 {@code WAITING_SHIPMENT}로 설정한다.
     * 배송 완료, 거래 종료, 취소와 관련된 필드는 null로 둔다.
     * 엔티티 객체만 생성하며, 저장은 호출하는 쪽이 repository로 한다. 호출하는 쪽: {@code OrderService}의 결제 처리
     *
     * @param productId        구매한 상품의 ID
     * @param buyerId          구매자의 회원 ID
     * @param sellerId         판매자의 회원 ID
     * @param quantity         주문 수량
     * @param unitPrice        적용 단가
     * @param paidAmount       실제 총 결제 금액
     * @param priceSource      단가의 출처
     * @param offerId          적용한 가격 제안의 ID. 등록 가격을 적용했으면 null
     * @param recipientName    수령인 이름
     * @param shippingAddress  배송 주소
     * @param confirmedAt      구매 확정 시각
     * @param shipDeadlineAt   발송 기한. 호출하는 쪽이 {@code TimeRules.shipDeadlineAt(confirmedAt)}으로 계산한 값
     * @param requestId        결제 요청 식별자
     * @return 아직 저장하지 않은 주문 엔티티. {@code orderId}는 저장할 때 생성된다.
     */
    public static PurchaseOrder place(Long productId, String buyerId, String sellerId, long quantity, long unitPrice,
                                      long paidAmount, PriceSource priceSource, Long offerId, String recipientName,
                                      String shippingAddress, Instant confirmedAt, Instant shipDeadlineAt,
                                      String requestId) {
        PurchaseOrder order = new PurchaseOrder();
        order.productId = productId;
        order.buyerId = buyerId;
        order.sellerId = sellerId;
        order.quantity = quantity;
        order.unitPrice = unitPrice;
        order.paidAmount = paidAmount;
        order.priceSource = priceSource;
        order.offerId = offerId;
        order.recipientName = recipientName;
        order.shippingAddress = shippingAddress;
        order.shippingStatus = ShippingStatus.WAITING_SHIPMENT;
        order.tradeStatus = TradeStatus.IN_PROGRESS;
        order.confirmedAt = confirmedAt;
        order.shipDeadlineAt = shipDeadlineAt;
        order.requestId = requestId;
        return order;
    }

    /**
     * 회원이 이 주문의 구매자인지 확인한다.
     *
     * @param memberId 확인할 회원 ID
     * @return 구매자이면 {@code true}
     */
    public boolean isBuyer(String memberId) {
        return buyerId.equals(memberId);
    }

    /**
     * 회원이 이 주문의 판매자인지 확인한다.
     *
     * @param memberId 확인할 회원 ID
     * @return 판매자이면 {@code true}
     */
    public boolean isSeller(String memberId) {
        return sellerId.equals(memberId);
    }

    /**
     * 회원이 이 주문의 구매자 또는 판매자인지 확인한다.
     *
     * @param memberId 확인할 회원 ID
     * @return 구매자 또는 판매자이면 {@code true}
     */
    public boolean isParty(String memberId) {
        return isBuyer(memberId) || isSeller(memberId);
    }

    /**
     * 거래가 최종 상태인지 확인한다.
     *
     * 보류({@code ON_HOLD})는 최종 상태가 아니므로 {@code false}를 반환한다.
     *
     * @return 거래 상태가 {@code COMPLETED}, {@code REFUNDED}, {@code CANCELLED} 중 하나이면 {@code true}
     */
    public boolean isFinalized() {
        return tradeStatus.isFinal();
    }

    /**
     * 배송 상태를 배송 중({@code SHIPPING})으로 변경한다.
     *
     * 주문의 배송 상태만 변경한다. 발송 정보({@code Shipment}) 행 생성, 발송 기한 확인, 발송 정보 중복 확인은 호출하는 쪽이 이 메서드보다 먼저 한다.
     * 호출하는 쪽: {@code ShipmentService.register}
     *
     * @throws IllegalStateException 진행 중({@code IN_PROGRESS})이면서 발송 대기({@code WAITING_SHIPMENT})인 주문이 아니면 던진다.
     */
    public void markShipped() {
        requireState(tradeStatus == TradeStatus.IN_PROGRESS && shippingStatus == ShippingStatus.WAITING_SHIPMENT);
        shippingStatus = ShippingStatus.SHIPPING;
    }

    /**
     * 배송 상태를 배송 완료({@code DELIVERED})로 변경하고 배송 완료 시각과 상품 확인 기한을 저장한다.
     *
     * 거래 상태는 {@code IN_PROGRESS}로 유지한다. 배송 완료만으로는 거래를 완료하지 않고 판매대금도 지급하지 않는다.
     * 호출하는 쪽: {@code ShipmentService.deliverIfDue}
     *
     * @param deliveredAt          배송 완료 시각
     * @param inspectionDeadlineAt 상품 확인 기한. 호출하는 쪽이 {@code TimeRules.inspectionDeadlineAt(deliveredAt)}으로 계산한 값
     * @throws IllegalStateException 진행 중이면서 배송 중({@code SHIPPING})이고 배송 완료 시각이 아직 없는 주문이 아니면 던진다.
     */
    public void markDelivered(Instant deliveredAt, Instant inspectionDeadlineAt) {
        requireState(tradeStatus == TradeStatus.IN_PROGRESS && shippingStatus == ShippingStatus.SHIPPING
                && this.deliveredAt == null);
        this.shippingStatus = ShippingStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
        this.inspectionDeadlineAt = inspectionDeadlineAt;
    }

    /**
     * 주문을 취소 완료(CANCELLED)로 바꾸고 취소 주체, 사유, 종료 시각을 기록한다.
     *
     * 엔티티 필드만 바꾼다. DB 반영은 flush 때 dirty checking으로 실행되는 UPDATE가 한다.
     * 배송 상태는 {@code WAITING_SHIPMENT}로 그대로 둔다.
     *
     * @param by     취소 주체
     * @param reason 취소 사유
     * @param now    거래 종료 시각(FINALIZED_AT)
     * @throws IllegalStateException 진행 중(IN_PROGRESS)이면서 발송 대기(WAITING_SHIPMENT)인 주문이 아니면 던진다.
     */
    public void cancel(CancelledBy by, String reason, Instant now) {
        // 검사는 이 트랜잭션이 조회한 엔티티의 필드 값으로 한다. 다른 트랜잭션이 커밋한 변경은 반영되지 않는다.
        requireState(tradeStatus == TradeStatus.IN_PROGRESS && shippingStatus == ShippingStatus.WAITING_SHIPMENT);
        this.tradeStatus = TradeStatus.CANCELLED;
        this.cancelledBy = by;
        this.cancelReason = reason;
        this.finalizedAt = now;
    }

    /**
     * 거래 상태를 보류({@code ON_HOLD})로 변경한다.
     *
     * 환불 요청을 접수할 때 호출한다. 상품 확인 기한 확인, 환불 요청 중복 확인, 환불 요청({@code RefundRequest}) 행 생성은 호출하는 쪽이 이 메서드보다 먼저 한다.
     * 보류 상태에서 {@link #complete}는 판매자 환불 거절({@code REFUND_REJECTED})만 허용한다. 그래서 보류 중에는 구매 확정과 확인 기간 만료 자동 완료가 실패한다.
     * 호출하는 쪽: {@code RefundService.requestRefund}
     *
     * @throws IllegalStateException 진행 중이면서 배송 완료({@code DELIVERED})인 주문이 아니면 던진다.
     */
    public void hold() {
        requireState(tradeStatus == TradeStatus.IN_PROGRESS && shippingStatus == ShippingStatus.DELIVERED);
        this.tradeStatus = TradeStatus.ON_HOLD;
    }

    /**
     * 거래 상태를 정상 완료({@code COMPLETED})로 변경하고 완료 사유와 거래 종료 시각을 저장한다.
     *
     * 완료 사유에 따라 허용하는 현재 거래 상태가 다르다.
     * - {@code BUYER_CONFIRMED}(구매 확정), {@code AUTO_EXPIRED}(확인 기간 만료): {@code IN_PROGRESS}에서만 허용한다.
     * - {@code REFUND_REJECTED}(판매자 환불 거절): {@code ON_HOLD}에서만 허용한다.
     * 두 경우 모두 배송 상태가 {@code DELIVERED}여야 한다.
     * 판매자에게 판매대금을 지급하는 처리는 호출하는 쪽이 한다. 호출하는 쪽: {@code TradeCompletionService.complete}
     *
     * @param cause 정상 완료 사유
     * @param now   거래 종료 시각(FINALIZED_AT)
     * @throws IllegalStateException 완료 사유와 현재 상태의 조합이 위 조건에 맞지 않으면 던진다.
     */
    public void complete(CompletionCause cause, Instant now) {
        boolean fromInProgress = tradeStatus == TradeStatus.IN_PROGRESS && cause != CompletionCause.REFUND_REJECTED;
        boolean fromHold = tradeStatus == TradeStatus.ON_HOLD && cause == CompletionCause.REFUND_REJECTED;
        requireState(shippingStatus == ShippingStatus.DELIVERED && (fromInProgress || fromHold));
        this.tradeStatus = TradeStatus.COMPLETED;
        this.completionCause = cause;
        this.finalizedAt = now;
    }

    /**
     * 거래 상태를 환불 완료({@code REFUNDED})로 변경하고 거래 종료 시각을 저장한다.
     *
     * 판매자 환불 동의와 무응답 자동 환불에서 호출한다. 배송 상태는 {@code DELIVERED}로 유지한다.
     * 환불 요청의 처리 결과 저장과 구매자 잔액 반환은 호출하는 쪽이 한다. 호출하는 쪽: {@code RefundService.refund}
     *
     * @param now 거래 종료 시각(FINALIZED_AT)
     * @throws IllegalStateException 보류({@code ON_HOLD})인 주문이 아니면 던진다.
     */
    public void refund(Instant now) {
        requireState(tradeStatus == TradeStatus.ON_HOLD);
        this.tradeStatus = TradeStatus.REFUNDED;
        this.finalizedAt = now;
    }

    /**
     * 상태 전환이 허용되는지 확인한다. 서비스가 현재 상태를 먼저 검사한다는 전제로 둔 마지막 확인이다.
     *
     * @param allowed 허용 조건의 계산 결과
     * @throws IllegalStateException {@code allowed}가 {@code false}이면 던진다.
     */
    private void requireState(boolean allowed) {
        if (!allowed) {
            throw new IllegalStateException("허용되지 않는 주문 상태 전환입니다. orderId=" + orderId
                    + ", tradeStatus=" + tradeStatus + ", shippingStatus=" + shippingStatus);
        }
    }
}
