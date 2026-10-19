package com.chatservice.marketplace.testsupport;

/**
 * 겹침 재현 테스트에서 요청을 일시 중지할 수 있는 위치.
 *
 * 요청 헤더 {@link TestPause#PAUSE_AT_HEADER}에 이 이름을 넣으면, 그 요청은 이 위치에 도달했을 때 멈춘다.
 */
public enum PausePoint {

    /** 주문을 조회한 직후, 상태를 검사하기 전. 당사자 취소(cancelByParty)와 발송 등록(register)에서 멈춘다. */
    ORDER_READ,

    /** 취소 처리에서 구매자 지갑을 조회하고 잔액 범위를 검사한 직후, 주문과 잔액을 바꾸기 전. */
    WALLET_READ
}
