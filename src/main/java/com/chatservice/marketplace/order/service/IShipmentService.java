package com.chatservice.marketplace.order.service;

import java.time.Instant;

public interface IShipmentService {

    /** F-011 발송 정보 등록. 주문당 한 번이며 등록하면 배송 중이 되고 모의 배송이 시작된다. */
    void register(String memberId, Long orderId, String carrierName, String trackingNumber, String requestId);

    /**
     * 모의 배송 완료 예정 시각이 지난 배송 중 주문을 배송 완료로 변경한다.
     *
     * {@code TradeScheduler}가 일정 간격으로 호출한다.
     *
     * @param now 대상 조회와 상태 재확인에 사용할 현재 시각
     * @return 이번 실행에서 배송 완료로 변경한 주문 수
     */
    int completeDueDeliveries(Instant now);
}
