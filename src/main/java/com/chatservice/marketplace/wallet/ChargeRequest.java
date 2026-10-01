package com.chatservice.marketplace.wallet;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 테스트 잔액 충전 요청(F-003). requestId 는 저장만 하고 중복 판단에 쓰지 않는다. */
public record ChargeRequest(
		@NotNull(message = "충전 금액은 필수입니다.")
		@Positive(message = "0보다 큰 정수여야 합니다.")
		Long amount,

		@Size(max = 64, message = "64자 이하여야 합니다.")
		String requestId) {
}
