package com.chatservice.marketplace.conversation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatservice.marketplace.conversation.domain.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByProductIdAndBuyerId(Long productId, String buyerId);

    List<Conversation> findByProductIdOrderByConversationIdAsc(Long productId);

    /* 내 채팅 목록: 구매 희망자 또는 판매자로 참여한 대화, 생성 시각 내림차순(설계 5.2.7) */
    @Query("select c from Conversation c where c.buyerId = :memberId or c.sellerId = :memberId"
            + " order by c.createdAt desc, c.conversationId desc")
    List<Conversation> findAllForMember(@Param("memberId") String memberId);
}
