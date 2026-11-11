package com.chatservice.marketplace.testsupport;

/**
 * 겹침 재현 테스트에서 요청을 일시 중지할 수 있는 위치.
 *
 * 요청 헤더 {@link TestPause#PAUSE_AT_HEADER}에 이 이름을 넣으면, 그 요청은 이 위치에 도달했을 때 멈춘다.
 */
public enum PausePoint {

    /** 주문을 조회한 직후, 상태를 검사하기 전. 당사자 취소(cancelByParty)와 발송 등록(register)에서 멈춘다. */
    ORDER_READ,

    /** 취소 처리에서 구매자 지갑을 조회하고 잔액 범위를 검사한 직후, 주문과 잔액을 바꾸기 전. 당사자 취소와 미발송 자동 취소가 함께 쓰는 공통 취소 처리(cancel)에 있다. */
    WALLET_READ,

    /** 결제(placeOrder)에서 상품, 예약, 제안, 지갑을 읽어 모든 검사를 통과한 직후, 주문을 저장하기 전. */
    PAYMENT_CHECKED,

    /** 결제(placeOrder)의 마지막 처리가 끝나고 트랜잭션이 커밋되기 직전. 이때까지 실행된 UPDATE의 행 잠금을 유지한 채 멈춘다. */
    PAYMENT_BEFORE_COMMIT,

    /** 예약 걸기(reserve)에서 같은 구매자의 예약과 구매 가능 수량 검사를 통과한 직후, 예약을 저장하기 전. 제안 수락이 같은 트랜잭션에서 지난다. */
    RESERVATION_CHECKED,

    /** 공통 취소 처리(cancel)에서 상품을 처음 조회한 직후, 남은 수량을 되돌리기 전. 당사자 취소와 미발송 자동 취소가 함께 지난다. */
    CANCEL_PRODUCT_READ
}
