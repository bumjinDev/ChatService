package com.chatservice.marketplace.conversation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	List<ChatMessage> findByConversationIdOrderByMessageIdAsc(Long conversationId);

	List<ChatMessage> findByConversationIdAndMessageIdGreaterThanOrderByMessageIdAsc(Long conversationId,
			Long afterId);
}
