package com.chatservice.marketplace.offer;

import java.time.Instant;

/** 가격 제안 응답 항목(설계 명세서 5.2.8, 5.2.11, 5.2.12). */
public record OfferResponse(
		Long offerId,
		long amount,
		OfferStatus status,
		Instant createdAt,
		Instant respondedAt) {

	public static OfferResponse of(PriceOffer offer) {
		return new OfferResponse(offer.getOfferId(), offer.getAmount(), offer.getStatus(), offer.getCreatedAt(),
				offer.getRespondedAt());
	}
}
