package com.chatservice.marketplace.offer.domain;

/**
 * 가격 제안의 응답 상태. PRICE_OFFER.STATUS 컬럼에 문자열로 저장한다.
 *
 * {@code PENDING}에서 {@code ACCEPTED} 또는 {@code REJECTED}로 한 번만 바뀐다.
 * 새 제안은 대화에 {@code PENDING} 제안이 없고, {@code ACCEPTED} 제안의 합의가 모두 만료 또는 해제일 때만 만들 수 있다.
 *
 * @see PriceOffer
 * @see AgreementStatus
 */
public enum OfferStatus {
    /** 응답 대기. 판매자의 수락 또는 거절을 기다린다. */
    PENDING,
    /** 수락. 합의가 성립해 제안 금액이 해당 구매자의 합의 단가가 되고 제안 수량만큼 예약이 걸린다. 합의 상태는 {@link AgreementStatus}로 계산한다. */
    ACCEPTED,
    /** 거절. 구매 희망자는 새 제안을 만들 수 있다. */
    REJECTED
}
