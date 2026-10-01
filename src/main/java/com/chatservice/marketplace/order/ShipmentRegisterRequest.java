package com.chatservice.marketplace.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 발송 정보 등록 요청(F-011). 실제 택배사 목록이나 운송장 형식은 검증하지 않는다. */
public record ShipmentRegisterRequest(
		@NotBlank(message = "택배사명은 필수입니다.")
		@Size(max = 100, message = "택배사명은 100자 이하여야 합니다.")
		String carrierName,

		@NotBlank(message = "운송장 번호는 필수입니다.")
		@Size(max = 100, message = "운송장 번호는 100자 이하여야 합니다.")
		String trackingNumber,

		@Size(max = 64, message = "64자 이하여야 합니다.")
		String requestId) {
}
