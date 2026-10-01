package com.chatservice.marketplace.conversation;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** CHAT_MESSAGE 테이블. 한 대화 안에서는 MESSAGE_ID 오름차순이 조회 순서다. */
@Entity
@Table(name = "CHAT_MESSAGE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "MESSAGE_ID")
	private Long messageId;

	@Column(name = "CONVERSATION_ID", nullable = false)
	private Long conversationId;

	@Column(name = "SENDER_ID", nullable = false)
	private String senderId;

	@Column(name = "CONTENT", nullable = false, length = 1000)
	private String content;

	@Column(name = "CLIENT_MESSAGE_ID", length = 64)
	private String clientMessageId;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	public static ChatMessage write(Long conversationId, String senderId, String content, String clientMessageId,
			Instant now) {
		ChatMessage message = new ChatMessage();
		message.conversationId = conversationId;
		message.senderId = senderId;
		message.content = content;
		message.clientMessageId = clientMessageId;
		message.createdAt = now;
		return message;
	}
}
