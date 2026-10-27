package com.chatservice.marketplace.reservation.dto;

import java.time.Instant;

import com.chatservice.marketplace.common.PartyRole;
import com.chatservice.marketplace.reservation.domain.Reservation;
import com.chatservice.marketplace.reservation.domain.ReservationStatus;

/**
 * 예약 응답. 예약 해제의 응답이며, 대화 상세의 reservation 과 가격 제안 수락 응답의 reservation 도 같은 형식이다.
 * offerId 는 이 예약을 만든 가격 제안 ID 이며, 판매자의 예약 걸기로 만들어진 기존 예약은 null 이다.
 * endedAt, releasedBy, fulfilledOrderId 는 해당하는 상태가 아니면 null 이다.
 */
public record ReservationResponse(
        Long reservationId,
        Long conversationId,
        Long productId,
        Long offerId,
        long quantity,
        ReservationStatus status,
        Instant reservedAt,
        Instant expiresAt,
        Instant endedAt,
        PartyRole releasedBy,
        Long fulfilledOrderId) {

    public static ReservationResponse of(Reservation reservation) {
        return new ReservationResponse(reservation.getReservationId(), reservation.getConversationId(),
                reservation.getProductId(), reservation.getOfferId(), reservation.getQuantity(), reservation.getStatus(),
                reservation.getReservedAt(), reservation.getExpiresAt(), reservation.getEndedAt(),
                reservation.getReleasedBy(), reservation.getFulfilledOrderId());
    }
}
