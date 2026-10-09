package com.chatservice.marketplace.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 발송 전 주문 취소(F-012, 설계 5.2.17). 사유는 자유 입력이며 누락할 수 없다. */
public record CancelRequest(
        @NotBlank(message = "취소 사유는 필수입니다.")
        @Size(max = 500, message = "취소 사유는 500자 이하여야 합니다.")
        String reason) {
}
