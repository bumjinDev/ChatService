package com.chatservice.marketplace.offer;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceOfferRepository extends JpaRepository<PriceOffer, Long> {

	List<PriceOffer> findByConversationIdOrderByCreatedAtAscOfferIdAsc(Long conversationId);

	boolean existsByConversationIdAndStatus(Long conversationId, OfferStatus status);
}
