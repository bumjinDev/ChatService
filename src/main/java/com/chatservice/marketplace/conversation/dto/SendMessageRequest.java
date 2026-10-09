package com.chatservice.marketplace.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 메시지 전송 요청(설계 5.2.10). 공백만으로 된 내용은 빈 메시지로 보고 거절한다. */
public record SendMessageRequest(
        @NotBlank(message = "메시지 내용은 비어 있을 수 없습니다.")
        @Size(max = 1000, message = "메시지는 1000자 이하여야 합니다.")
        String content,

        @Size(max = 64, message = "requestId 는 64자 이하여야 합니다.")
        String requestId) {
}
