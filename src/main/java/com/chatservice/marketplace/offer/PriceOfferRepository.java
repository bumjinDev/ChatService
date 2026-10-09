package com.chatservice.marketplace.offer;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatservice.marketplace.offer.domain.OfferStatus;
import com.chatservice.marketplace.offer.domain.PriceOffer;

public interface PriceOfferRepository extends JpaRepository<PriceOffer, Long> {

    /* 대화 상세의 제안 내역: 생성 시각 오름차순(설계 6.7) */
    List<PriceOffer> findByConversationIdOrderByCreatedAtAscOfferIdAsc(Long conversationId);

    boolean existsByConversationIdAndStatus(Long conversationId, OfferStatus status);
}
