package com.chatservice.marketplace.conversation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

	Optional<Conversation> findFirstByProductIdAndBuyerIdOrderByConversationIdAsc(Long productId, String buyerId);

	List<Conversation> findByProductIdOrderByConversationIdAsc(Long productId);

	@Query("SELECT c FROM Conversation c WHERE c.buyerId = :memberId OR c.sellerId = :memberId "
			+ "ORDER BY c.createdAt DESC, c.conversationId DESC")
	List<Conversation> findAllOfMember(@Param("memberId") String memberId);
}
