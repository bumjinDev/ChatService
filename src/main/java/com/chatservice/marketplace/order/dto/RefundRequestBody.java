package com.chatservice.marketplace.order.dto;

import com.chatservice.marketplace.order.domain.OrderEnums.RefundReasonCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 환불 요청(F-017, 설계 5.2.19). 사유는 DESCRIPTION_MISMATCH 하나이며 사진 증빙은 받지 않는다. */
public record RefundRequestBody(
        @NotNull(message = "환불 사유는 필수입니다.")
        RefundReasonCode reasonCode,

        @NotBlank(message = "상세 설명은 필수입니다.")
        @Size(max = 2000, message = "상세 설명은 2000자 이하여야 합니다.")
        String detail,

        @Size(max = 64, message = "requestId 는 64자 이하여야 합니다.")
        String requestId) {
}
