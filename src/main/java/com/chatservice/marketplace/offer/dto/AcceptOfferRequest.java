package com.chatservice.marketplace.offer.dto;

import com.chatservice.marketplace.common.AmountRules;

import jakarta.validation.constraints.Max;

/**
 * 가격 제안 수락 요청의 본문. 본문은 선택이며 없으면 수락만 한다.
 * reserve 가 true 이면 수락과 함께 같은 트랜잭션에서 quantity 만큼 그 구매 희망자에게 예약을 건다.
 * reserve 가 true 일 때 quantity 가 없거나 1 미만이면 컨트롤러가 400 으로 거절한다.
 */
public record AcceptOfferRequest(
        Boolean reserve,

        @Max(value = AmountRules.MAX_QUANTITY, message = "예약 수량이 저장 가능한 범위를 초과합니다.")
        Long quantity) {

    public boolean wantsReservation() {
        return Boolean.TRUE.equals(reserve);
    }
}
