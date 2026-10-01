package com.chatservice.marketplace.offer;

import java.time.Instant;

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

/** PRICE_OFFER 테이블. 구매 희망자의 제안과 판매자의 응답이다. 수락된 제안의 금액이 합의 가격이다. */
@Entity
@Table(name = "PRICE_OFFER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceOffer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "OFFER_ID")
	private Long offerId;

	@Column(name = "CONVERSATION_ID", nullable = false)
	private Long conversationId;

	@Column(name = "PRODUCT_ID", nullable = false)
	private Long productId;

	@Column(name = "BUYER_ID", nullable = false)
	private String buyerId;

	@Column(name = "SELLER_ID", nullable = false)
	private String sellerId;

	@Column(name = "AMOUNT", nullable = false)
	private long amount;

	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false, length = 30)
	private OfferStatus status;

	@Column(name = "REQUEST_ID", length = 64)
	private String requestId;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	@Column(name = "RESPONDED_AT")
	private Instant respondedAt;

	public static PriceOffer propose(Long conversationId, Long productId, String buyerId, String sellerId,
			long amount, String requestId, Instant now) {
		PriceOffer offer = new PriceOffer();
		offer.conversationId = conversationId;
		offer.productId = productId;
		offer.buyerId = buyerId;
		offer.sellerId = sellerId;
		offer.amount = amount;
		offer.status = OfferStatus.PENDING;
		offer.requestId = requestId;
		offer.createdAt = now;
		return offer;
	}

	public void respond(boolean accept, Instant now) {
		this.status = accept ? OfferStatus.ACCEPTED : OfferStatus.REJECTED;
		this.respondedAt = now;
	}
}
