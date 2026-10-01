package com.chatservice.marketplace.conversation;

import java.time.Instant;

/** 저장된 메시지(설계 명세서 5.2.9, 5.2.10). */
public record MessageResponse(
		Long messageId,
		String senderId,
		String senderNickname,
		String content,
		Instant createdAt) {

	public static MessageResponse of(ChatMessage message, String senderNickname) {
		return new MessageResponse(message.getMessageId(), message.getSenderId(), senderNickname,
				message.getContent(), message.getCreatedAt());
	}
}
