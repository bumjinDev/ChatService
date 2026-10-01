package com.chatservice.marketplace.offer;

public interface IOfferService {

	/** 대화의 구매 희망자가 등록 가격보다 낮은 가격을 제안한다(F-008). */
	OfferResponse propose(String memberId, Long conversationId, OfferProposeRequest request);

	/** 판매자가 응답 대기 제안을 수락(accept=true)하거나 거절한다(F-009). */
	OfferResponse respond(String memberId, Long offerId, boolean accept);

	/**
	 * 결제에 쓸 합의 가격을 검증한다. 제안이 없거나, ACCEPTED 가 아니거나, 다른 상품 또는 다른 구매자의 제안이면
	 * 409 INVALID_OFFER_SELECTION 이다(F-010).
	 */
	PriceOffer requireAgreedOffer(Long offerId, Long productId, String buyerId);
}
