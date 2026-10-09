package com.chatservice.marketplace.conversation.dto;

import java.time.Instant;

/** 메시지 내역 항목(설계 5.2.9). */
public record MessageResponse(Long messageId, String senderId, String senderNickname, String content, Instant createdAt) {
}
