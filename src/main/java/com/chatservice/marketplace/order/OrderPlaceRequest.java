package com.chatservice.marketplace.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 구매·잔액 결제 요청(F-010). offerId 가 없으면 등록 가격을 적용한다. */
public record OrderPlaceRequest(
		@NotNull(message = "상품은 필수입니다.")
		Long productId,

		@NotBlank(message = "수령인은 필수입니다.")
		@Size(max = 100, message = "수령인은 100자 이하여야 합니다.")
		String recipientName,

		@NotBlank(message = "배송 주소는 필수입니다.")
		@Size(max = 500, message = "배송 주소는 500자 이하여야 합니다.")
		String shippingAddress,

		Long offerId,

		@Size(max = 64, message = "64자 이하여야 합니다.")
		String requestId) {
}
