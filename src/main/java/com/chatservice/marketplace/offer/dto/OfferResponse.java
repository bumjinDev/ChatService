package com.chatservice.marketplace.offer.dto;

import java.time.Instant;

import com.chatservice.marketplace.offer.domain.OfferStatus;
import com.chatservice.marketplace.offer.domain.PriceOffer;
import com.chatservice.marketplace.reservation.dto.ReservationResponse;

/**
 * 가격 제안(설계 5.2.11, 5.2.12). quantity 는 제안 수량이고, respondedAt 은 응답 전이면 null.
 * reservation 은 수락과 함께 제안 수량만큼 건 예약이며, 제안·거절 응답에서는 null 이다.
 */
public record OfferResponse(Long offerId, Long conversationId, long amount, long quantity, OfferStatus status,
                            Instant createdAt, Instant respondedAt, ReservationResponse reservation) {

    public static OfferResponse of(PriceOffer offer) {
        return of(offer, null);
    }

    public static OfferResponse of(PriceOffer offer, ReservationResponse reservation) {
        return new OfferResponse(offer.getOfferId(), offer.getConversationId(), offer.getAmount(), offer.getQuantity(),
                offer.getStatus(), offer.getCreatedAt(), offer.getRespondedAt(), reservation);
    }
}
