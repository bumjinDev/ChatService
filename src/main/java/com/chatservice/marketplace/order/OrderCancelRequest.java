package com.chatservice.marketplace.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 발송 전 주문 취소 요청(F-012). 취소 사유는 필수다. */
public record OrderCancelRequest(
		@NotBlank(message = "취소 사유는 필수입니다.")
		@Size(max = 500, message = "취소 사유는 500자 이하여야 합니다.")
		String reason) {
}
