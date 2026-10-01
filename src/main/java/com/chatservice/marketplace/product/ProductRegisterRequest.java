package com.chatservice.marketplace.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 상품 등록 요청(F-001). 가격은 0보다 큰 정수다. */
public record ProductRegisterRequest(
		@NotBlank(message = "상품명은 필수입니다.")
		@Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
		String name,

		@NotBlank(message = "설명은 필수입니다.")
		String description,

		@NotNull(message = "카테고리는 필수입니다.")
		Category category,

		@NotNull(message = "가격은 필수입니다.")
		@Positive(message = "0보다 큰 정수여야 합니다.")
		Long price) {
}
