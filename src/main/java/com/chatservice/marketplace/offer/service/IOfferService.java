package com.chatservice.marketplace.offer.service;

import com.chatservice.marketplace.offer.domain.PriceOffer;
import com.chatservice.marketplace.offer.dto.OfferResponse;

public interface IOfferService {

    /** F-008 가격 제안. */
    OfferResponse propose(String memberId, Long conversationId, long amount, String requestId);

    /** F-009 판매자 수락(accept=true)·거절(accept=false). */
    OfferResponse respond(String memberId, Long offerId, boolean accept);

    /**
     * 결제 시 선택한 합의 가격 검증(F-010). 제안이 없거나, 수락되지 않았거나, 다른 상품·다른 구매자의 제안이면
     * INVALID_OFFER_SELECTION. 상품 판매 상태는 호출하는 쪽에서 먼저 검사한다.
     */
    PriceOffer acceptedOfferFor(Long offerId, Long productId, String buyerId);
}
