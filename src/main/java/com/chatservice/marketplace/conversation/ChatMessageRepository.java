package com.chatservice.marketplace.conversation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatservice.marketplace.conversation.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByMessageIdAsc(Long conversationId);

    /* 재접속 후 놓친 메시지만 가져온다: MESSAGE_ID > :afterId (설계 4.2 CHAT_MESSAGE) */
    List<ChatMessage> findByConversationIdAndMessageIdGreaterThanOrderByMessageIdAsc(Long conversationId, Long afterId);
}
