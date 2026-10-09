package com.chatservice.marketplace.offer.dto;

import java.time.Instant;

import com.chatservice.marketplace.offer.domain.OfferStatus;
import com.chatservice.marketplace.offer.domain.PriceOffer;

/** 가격 제안(설계 5.2.11, 5.2.12). respondedAt 은 응답 전이면 null. */
public record OfferResponse(Long offerId, Long conversationId, long amount, OfferStatus status,
                            Instant createdAt, Instant respondedAt) {

    public static OfferResponse of(PriceOffer offer) {
        return new OfferResponse(offer.getOfferId(), offer.getConversationId(), offer.getAmount(), offer.getStatus(),
                offer.getCreatedAt(), offer.getRespondedAt());
    }
}
