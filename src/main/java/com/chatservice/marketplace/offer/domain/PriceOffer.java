package com.chatservice.marketplace.offer.domain;

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

/**
 * 가격 제안(PRICE_OFFER). ACCEPTED 제안의 금액이 해당 상품·구매자 전용 개당 합의 가격이다.
 * 수락에는 만료·철회·재협상이 없고, 같은 구매자의 여러 주문에 다시 쓸 수 있다(F-009 추가 규칙).
 */
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
    @Column(name = "STATUS", nullable = false)
    private OfferStatus status;

    @Column(name = "REQUEST_ID")
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

    public boolean isPending() {
        return status == OfferStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == OfferStatus.ACCEPTED;
    }

    public void respond(boolean accept, Instant now) {
        if (!isPending()) {
            throw new IllegalStateException("이미 응답한 제안입니다. offerId=" + offerId);
        }
        status = accept ? OfferStatus.ACCEPTED : OfferStatus.REJECTED;
        respondedAt = now;
    }
}
