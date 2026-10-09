package com.chatservice.marketplace.order.service;

import java.time.Instant;

import com.chatservice.marketplace.order.domain.OrderEnums.RefundReasonCode;

public interface IRefundService {

    /** F-017 환불 요청과 거래 보류. */
    void requestRefund(String memberId, Long orderId, RefundReasonCode reasonCode, String detail, String requestId);

    /** F-018 판매자 판단. approve=true 는 반품 없는 전액 환불, false 는 정상 완료·판매대금 지급(CH-001). */
    void respond(String memberId, Long orderId, boolean approve);

    /**
     * 판매자가 응답 기한까지 응답하지 않은 환불 요청을 자동 승인하고 구매자에게 결제 금액 전액을 반환한다.
     *
     * {@code TradeScheduler}가 일정 간격으로 호출한다.
     *
     * @param now 대상 조회와 상태 재확인에 사용할 현재 시각
     * @return 이번 실행에서 환불 완료한 건수
     */
    int autoApproveExpired(Instant now);
}
