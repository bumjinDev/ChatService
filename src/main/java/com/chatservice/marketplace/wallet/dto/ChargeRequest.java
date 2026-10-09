package com.chatservice.marketplace.wallet.dto;

import com.chatservice.marketplace.common.AmountRules;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 테스트 잔액 충전 요청(F-003, 설계 5.2.4). */
public record ChargeRequest(
        @NotNull(message = "충전 금액은 필수입니다.")
        @Min(value = 1, message = "충전 금액은 0보다 큰 정수여야 합니다.")
        @Max(value = AmountRules.MAX_AMOUNT, message = "충전 금액이 저장 가능한 범위를 초과합니다.")
        Long amount,

        @Size(max = 64, message = "requestId 는 64자 이하여야 합니다.")
        String requestId) {
}
