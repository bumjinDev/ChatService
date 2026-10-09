package com.chatservice.marketplace.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 발송 정보 등록(F-011, 설계 5.2.16). 실제 택배사 목록과 운송장 형식은 검증하지 않는다. */
public record ShipmentRequest(
        @NotBlank(message = "택배사명은 필수입니다.")
        @Size(max = 100, message = "택배사명은 100자 이하여야 합니다.")
        String carrierName,

        @NotBlank(message = "운송장 번호는 필수입니다.")
        @Size(max = 100, message = "운송장 번호는 100자 이하여야 합니다.")
        String trackingNumber,

        @Size(max = 64, message = "requestId 는 64자 이하여야 합니다.")
        String requestId) {
}
