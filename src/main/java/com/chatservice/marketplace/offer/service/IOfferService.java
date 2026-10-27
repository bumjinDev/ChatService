package com.chatservice.marketplace.offer.service;

import com.chatservice.marketplace.offer.domain.PriceOffer;
import com.chatservice.marketplace.offer.dto.OfferResponse;

public interface IOfferService {

    /** F-008 가격 제안. */
    OfferResponse propose(String memberId, Long conversationId, long amount, String requestId);

    /**
     * F-009 판매자 수락(accept=true)·거절(accept=false).
     * 수락에서 {@code reserveQuantity}가 있으면 같은 트랜잭션에서 그 수량만큼 구매 희망자에게 예약을 건다. 예약이 거절되면 수락도 롤백된다.
     * 거절에서는 {@code reserveQuantity}를 쓰지 않는다.
     */
    OfferResponse respond(String memberId, Long offerId, boolean accept, Long reserveQuantity);

    /**
     * 결제 시 선택한 합의 가격 검증(F-010). 제안이 없거나, 수락되지 않았거나, 다른 상품·다른 구매자의 제안이면
     * INVALID_OFFER_SELECTION. 상품 판매 상태는 호출하는 쪽에서 먼저 검사한다.
     */
    PriceOffer acceptedOfferFor(Long offerId, Long productId, String buyerId);
}
