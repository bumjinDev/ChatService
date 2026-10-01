package com.chatservice.marketplace.offer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 가격 제안 요청(F-008). 금액이 등록 가격보다 작은지는 서비스가 상품을 조회해 검사한다. */
public record OfferProposeRequest(
		@NotNull(message = "제안 금액은 필수입니다.")
		@Positive(message = "0보다 큰 정수여야 합니다.")
		Long amount,

		@Size(max = 64, message = "64자 이하여야 합니다.")
		String requestId) {
}
