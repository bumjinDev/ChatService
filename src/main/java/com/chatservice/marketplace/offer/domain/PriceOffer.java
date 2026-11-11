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
 * 가격 제안(PRICE_OFFER). 구매 희망자가 개당 금액과 수량을 함께 제안한다.
 * 판매자가 수락하면 합의가 성립해, 제안 금액이 해당 상품·구매자 전용 개당 합의 가격이 되고 제안 수량만큼 예약이 걸린다.
 * 합의 가격으로는 예약 중에 제안 수량과 같은 수량을 한 번 결제할 수 있고, 그 주문이 발송 전에 취소되면 한 번 더 결제할 수 있다.
 * 합의가 만료되거나 해제되면 구매 희망자는 새 제안을 할 수 있다. 합의 상태는 {@link AgreementStatus}다.
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

    /** 제안 수량. 수락하면 이 수량만큼 구매 희망자에게 예약이 걸리고, 합의 가격 결제는 이 수량과 같아야 한다. */
    @Column(name = "QUANTITY", nullable = false)
    private long quantity;

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
                                     long amount, long quantity, String requestId, Instant now) {
        PriceOffer offer = new PriceOffer();
        offer.conversationId = conversationId;
        offer.productId = productId;
        offer.buyerId = buyerId;
        offer.sellerId = sellerId;
        offer.amount = amount;
        offer.quantity = quantity;
        offer.status = OfferStatus.PENDING;
        offer.requestId = requestId;
        offer.createdAt = now;
        return offer;
    }

    public boolean isPending() {
        // 응답 대기인지 확인한다. PENDING : 수락 혹은 거부 대기중, ACCEPTED : 수락, REJECTED : 거부
        return status == OfferStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == OfferStatus.ACCEPTED;
    }

    /**
     * 응답 대기인 가격 제안을 수락 또는 거절로 바꾸고 응답 시각을 기록한다. 엔티티 필드만 바꾸며 UPDATE 는 flush 때 실행된다.
     *
     * @param accept 수락이면 {@code true}, 거절이면 {@code false}
     * @param now    응답 시각
     * @throws IllegalStateException 응답 대기가 아니면 던진다.
     */
    public void respond(boolean accept, Instant now) {
        // 응답 대기가 아닌 가격 제안은 바꾸지 않는다. 서비스가 먼저 확인한 뒤 호출한다
        if (!isPending()) {
            throw new IllegalStateException("이미 응답한 제안입니다. offerId=" + offerId);
        }
        // 수락이면 ACCEPTED, 거절이면 REJECTED 로 바꾼다
        status = accept ? OfferStatus.ACCEPTED : OfferStatus.REJECTED;
        // 응답 시각을 기록한다
        respondedAt = now;
    }
}
