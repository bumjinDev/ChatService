package com.chatservice.marketplace.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 메시지 전송 요청(F-006). 내용은 1~1000자이고 공백만으로 된 내용은 허용하지 않는다. */
public record MessageSendRequest(
		@NotBlank(message = "메시지 내용은 비어 있을 수 없습니다.")
		@Size(max = 1000, message = "메시지는 1000자 이하여야 합니다.")
		String content,

		@Size(max = 64, message = "64자 이하여야 합니다.")
		String requestId) {
}
