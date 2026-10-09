package com.chatservice.marketplace.conversation.dto;

import java.time.Instant;

/** 메시지 전송 결과(설계 5.2.10). 저장이 끝난 메시지만 이 응답으로 돌려준다. */
public record SentMessageResponse(Long messageId, String senderId, String content, Instant createdAt) {
}
