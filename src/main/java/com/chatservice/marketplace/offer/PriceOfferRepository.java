package com.chatservice.marketplace.offer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.offer.domain.OfferStatus;
import com.chatservice.marketplace.offer.domain.PriceOffer;

public interface PriceOfferRepository extends JpaRepository<PriceOffer, Long> {

    /* 대화 상세의 제안 내역: 생성 시각 오름차순(설계 6.7) */
    List<PriceOffer> findByConversationIdOrderByCreatedAtAscOfferIdAsc(Long conversationId);

    boolean existsByConversationIdAndStatus(Long conversationId, OfferStatus status);

    /* 새 제안의 허용 판단: 대화의 수락된 제안마다 합의 상태를 구한다 */
    List<PriceOffer> findByConversationIdAndStatus(Long conversationId, OfferStatus status);

    /* 가격 제안의 상품 ID 만 조회한다. 가격 제안 수락이 상품별 잠금을 얻기 전에 어느 상품의 요청인지 알아내는 데 쓴다. 가격 제안이 없으면 빈 값을 돌려준다.
       수락은 트랜잭션을 시작하기 전에 이 조회를 호출한다. 그때는 이 조회만 실행하는 읽기 전용 트랜잭션에서 실행되고, 조회가 끝나면 트랜잭션도 끝난다 */
    @Transactional(readOnly = true)
    @Query("select o.productId from PriceOffer o where o.offerId = :offerId")
    Optional<Long> findProductIdByOfferId(@Param("offerId") Long offerId);
}
