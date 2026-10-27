package com.chatservice.marketplace.offer.domain;

/**
 * 합의의 상태. 수락된 가격 제안과 그 제안으로 걸린 예약을 합의라고 부른다.
 *
 * 저장하는 열은 없다. 제안 행(PRICE_OFFER), 예약 행(RESERVATION.OFFER_ID 가 그 제안인 행),
 * 주문 행(PURCHASE_ORDER.OFFER_ID 가 그 제안인 행)에서 {@code AgreementQueryService}가 계산한다.
 * 응답 대기 제안과 거절된 제안에는 합의 상태가 없다.
 *
 * 구매 희망자는 합의가 {@code EXPIRED} 또는 {@code RELEASED}일 때만 새 가격 제안을 할 수 있다.
 */
public enum AgreementStatus {
    /** 예약 중. 그 제안의 예약이 활성이고 만료 시각 전이다. 합의 가격으로 제안 수량을 결제할 수 있고, 등록 가격으로는 결제할 수 없다. */
    RESERVED,
    /** 결제됨. 합의 가격으로 결제한 주문이 진행 중이거나 보류다. 그 주문이 발송 전에 취소되면 새 예약 행이 생겨 다시 예약 중이 된다. */
    ORDERED,
    /** 만료. 예약 기간이 지났거나, 합의 가격 주문이 정상 완료 또는 환불 완료로 끝났다. 다시 유효해지지 않는다. */
    EXPIRED,
    /** 해제. 판매자 또는 구매 희망자가 예약을 해제했다. 다시 유효해지지 않는다. */
    RELEASED
}
