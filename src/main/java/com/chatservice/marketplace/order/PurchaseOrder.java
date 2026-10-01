package com.chatservice.marketplace.order;

import java.time.Instant;

import com.chatservice.marketplace.common.MemberRole;

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
 * PURCHASE_ORDER 테이블. 구매 확정 사실과 배송 진행 상태(SHIPPING_STATUS), 거래 결과 상태(TRADE_STATUS)를 저장한다.
 * 상태를 바꾸는 메서드는 상태 검사를 하지 않는다. 허용 여부는 서비스가 먼저 검사한다.
 */
@Entity
@Table(name = "PURCHASE_ORDER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ORDER_ID")
	private Long orderId;

	@Column(name = "PRODUCT_ID", nullable = false)
	private Long productId;

	@Column(name = "BUYER_ID", nullable = false)
	private String buyerId;

	@Column(name = "SELLER_ID", nullable = false)
	private String sellerId;

	@Column(name = "PAID_AMOUNT", nullable = false)
	private long paidAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "PRICE_SOURCE", nullable = false, length = 30)
	private PriceSource priceSource;

	@Column(name = "OFFER_ID")
	private Long offerId;

	@Column(name = "RECIPIENT_NAME", nullable = false, length = 100)
	private String recipientName;

	@Column(name = "SHIPPING_ADDRESS", nullable = false, length = 500)
	private String shippingAddress;

	@Enumerated(EnumType.STRING)
	@Column(name = "SHIPPING_STATUS", nullable = false, length = 30)
	private ShippingStatus shippingStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "TRADE_STATUS", nullable = false, length = 30)
	private TradeStatus tradeStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "COMPLETION_CAUSE", length = 30)
	private CompletionCause completionCause;

	@Column(name = "CONFIRMED_AT", nullable = false)
	private Instant confirmedAt;

	@Column(name = "SHIP_DEADLINE_AT", nullable = false)
	private Instant shipDeadlineAt;

	@Column(name = "DELIVERED_AT")
	private Instant deliveredAt;

	@Column(name = "INSPECTION_DEADLINE_AT")
	private Instant inspectionDeadlineAt;

	@Column(name = "FINALIZED_AT")
	private Instant finalizedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "CANCELLED_BY", length = 30)
	private CancelledBy cancelledBy;

	@Column(name = "CANCEL_REASON", length = 500)
	private String cancelReason;

	@Column(name = "REQUEST_ID", length = 64)
	private String requestId;

	/** 결제 성공으로 주문을 만든다. 거래는 진행 중, 배송은 발송 대기로 시작한다. */
	public static PurchaseOrder confirm(Long productId, String buyerId, String sellerId, long paidAmount,
			PriceSource priceSource, Long offerId, String recipientName, String shippingAddress,
			Instant confirmedAt, Instant shipDeadlineAt, String requestId) {
		PurchaseOrder order = new PurchaseOrder();
		order.productId = productId;
		order.buyerId = buyerId;
		order.sellerId = sellerId;
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

	public boolean isParty(String memberId) {
		return buyerId.equals(memberId) || sellerId.equals(memberId);
	}

	public MemberRole roleOf(String memberId) {
		return buyerId.equals(memberId) ? MemberRole.BUYER : MemberRole.SELLER;
	}

	public void markShipping() {
		this.shippingStatus = ShippingStatus.SHIPPING;
	}

	/** 최초 배송 완료를 기록한다. 안내 시점과 상품 확인 기한은 이후 바꾸지 않는다. */
	public void markDelivered(Instant deliveredAt, Instant inspectionDeadlineAt) {
		this.shippingStatus = ShippingStatus.DELIVERED;
		this.deliveredAt = deliveredAt;
		this.inspectionDeadlineAt = inspectionDeadlineAt;
	}

	public void cancel(CancelledBy cancelledBy, String reason, Instant now) {
		this.tradeStatus = TradeStatus.CANCELLED;
		this.cancelledBy = cancelledBy;
		this.cancelReason = reason;
		this.finalizedAt = now;
	}

	public void complete(CompletionCause cause, Instant now) {
		this.tradeStatus = TradeStatus.COMPLETED;
		this.completionCause = cause;
		this.finalizedAt = now;
	}

	public void hold() {
		this.tradeStatus = TradeStatus.ON_HOLD;
	}

	public void refund(Instant now) {
		this.tradeStatus = TradeStatus.REFUNDED;
		this.finalizedAt = now;
	}
}
