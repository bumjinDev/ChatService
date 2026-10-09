package com.chatservice.marketplace.conversation.domain;

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

/**
 * 텍스트 메시지(CHAT_MESSAGE). 한 대화의 조회 순서는 messageId 오름차순이다(F-007 추가 규칙).
 * clientMessageId 는 저장만 하고 기본 구현은 재전송 식별에 쓰지 않는다(F-006-AC-02 는 후속 과제).
 */
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

    @Column(name = "CONTENT", nullable = false)
    private String content;

    @Column(name = "CLIENT_MESSAGE_ID")
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
