package com.chatservice.marketplace.order.service;

import java.time.Instant;

public interface IOrderCancellationService {

    /** F-012 발송 전 당사자 취소. 결제 금액 전액을 구매자에게 반환한다. */
    void cancelByParty(String memberId, Long orderId, String reason);

    /**
     * 발송 기한이 지나도록 발송 정보가 등록되지 않은 주문을 자동 취소한다.
     *
     * {@code TradeScheduler}가 일정 간격으로 호출한다.
     *
     * @param now 대상 조회와 상태 재확인에 사용할 현재 시각
     * @return 이번 실행에서 취소한 주문 수
     */
    int cancelExpiredUnshipped(Instant now);
}
