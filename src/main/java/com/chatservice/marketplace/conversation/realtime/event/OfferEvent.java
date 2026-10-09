package com.chatservice.marketplace.conversation.realtime.event;

import java.time.Instant;

/** 가격 제안 생성·수락·거절 직후 상대방 세션에 보내는 이벤트(설계 5.3 OFFER). */
public record OfferEvent(String type, Long offerId, Long conversationId, long amount, String status,
                         Instant createdAt, Instant respondedAt) {

    public static OfferEvent of(Long offerId, Long conversationId, long amount, String status,
                                Instant createdAt, Instant respondedAt) {
        return new OfferEvent("OFFER", offerId, conversationId, amount, status, createdAt, respondedAt);
    }
}
