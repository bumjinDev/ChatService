package com.chatservice.marketplace.reservation.dto;

import com.chatservice.marketplace.common.AmountRules;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 예약 걸기 요청. quantity 는 예약 수량이며 상한(남은 수량 − 다른 회원의 활성 예약 합계) 검사는 서비스가 한다. */
public record ReservationRequest(
        @NotNull(message = "예약 수량은 필수입니다.")
        @Min(value = 1, message = "예약 수량은 1 이상의 정수여야 합니다.")
        @Max(value = AmountRules.MAX_QUANTITY, message = "예약 수량이 저장 가능한 범위를 초과합니다.")
        Long quantity) {
}
