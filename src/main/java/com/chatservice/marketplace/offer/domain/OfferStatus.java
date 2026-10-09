package com.chatservice.marketplace.offer.domain;

/**
 * 가격 제안의 응답 상태. PRICE_OFFER.STATUS 컬럼에 문자열로 저장한다.
 *
 * {@code PENDING}에서 {@code ACCEPTED} 또는 {@code REJECTED}로 한 번만 바뀐다.
 * 새 제안은 대화에 {@code PENDING}이나 {@code ACCEPTED} 제안이 없을 때만 만들 수 있다.
 *
 * @see PriceOffer
 */
public enum OfferStatus {
    /** 응답 대기. 판매자의 수락 또는 거절을 기다린다. */
    PENDING,
    /** 수락. 제안 금액이 해당 구매자의 합의 단가가 되며, 같은 상품의 별도 주문에도 다시 사용할 수 있다. */
    ACCEPTED,
    /** 거절. 구매 희망자는 새 제안을 만들 수 있다. */
    REJECTED
}
