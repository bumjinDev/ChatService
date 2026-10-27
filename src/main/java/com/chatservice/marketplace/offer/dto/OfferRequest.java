package com.chatservice.marketplace.offer.dto;

import com.chatservice.marketplace.common.AmountRules;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 가격 제안 요청(F-008, 설계 5.2.11). amount 는 개당 제안 가격이며 등록 가격 미만 검사는 서비스가 한다.
 * quantity 는 제안 수량이며 구매 가능 수량 이하인지는 서비스가 검사한다.
 */
public record OfferRequest(
        @NotNull(message = "제안 금액은 필수입니다.")
        @Min(value = 1, message = "제안 금액은 0보다 큰 정수여야 합니다.")
        @Max(value = AmountRules.MAX_AMOUNT, message = "제안 금액이 저장 가능한 범위를 초과합니다.")
        Long amount,

        @NotNull(message = "제안 수량은 필수입니다.")
        @Min(value = 1, message = "제안 수량은 1 이상의 정수여야 합니다.")
        @Max(value = AmountRules.MAX_QUANTITY, message = "제안 수량이 저장 가능한 범위를 초과합니다.")
        Long quantity,

        @Size(max = 64, message = "requestId 는 64자 이하여야 합니다.")
        String requestId) {
}
