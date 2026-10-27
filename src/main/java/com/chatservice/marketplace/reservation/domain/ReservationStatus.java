package com.chatservice.marketplace.reservation.domain;

/**
 * 예약의 상태. RESERVATION.STATUS 컬럼에 문자열로 저장한다.
 *
 * {@code ACTIVE}에서 {@code RELEASED}, {@code EXPIRED}, {@code FULFILLED} 가운데 하나로 한 번만 바뀐다.
 * 끝난 예약은 지우지 않고 상태로 구분해 남긴다.
 *
 * @see Reservation
 */
public enum ReservationStatus {
    /** 활성. 만료 시각 전까지 예약된 구매 희망자만 예약 수량을 살 수 있다. */
    ACTIVE,
    /** 해제. 판매자 또는 예약된 구매 희망자가 해제했다. 해제 주체는 RELEASED_BY 에 있다. */
    RELEASED,
    /** 기간 만료. 만료 시각이 지나 자동 처리가 해제했다. */
    EXPIRED,
    /** 결제 종료. 예약된 구매 희망자가 결제해 예약이 끝났다. 그 주문은 FULFILLED_ORDER_ID 에 있다. */
    FULFILLED
}
