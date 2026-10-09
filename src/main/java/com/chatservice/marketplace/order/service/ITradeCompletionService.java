package com.chatservice.marketplace.order.service;

import java.time.Instant;

import com.chatservice.marketplace.order.domain.OrderEnums.CompletionCause;
import com.chatservice.marketplace.order.domain.PurchaseOrder;

public interface ITradeCompletionService {

    /** F-015 구매자의 정상 수령 확인과 판매대금 지급. */
    void confirmReceipt(String memberId, Long orderId);

    /**
     * 상품 확인 기한이 지나도록 구매 확정과 환불 요청이 없는 주문을 자동으로 정상 완료한다.
     *
     * {@code TradeScheduler}가 일정 간격으로 호출한다.
     *
     * @param now 대상 조회와 상태 재확인에 사용할 현재 시각
     * @return 이번 실행에서 정상 완료한 주문 수
     */
    int completeExpiredInspections(Instant now);

    /**
     * 주문을 정상 완료로 변경하고 판매자에게 결제 금액 전액을 판매대금으로 지급한다.
     *
     * 구매 확정, 확인 기간 만료 자동 완료, 판매자 환불 거절이 함께 사용하는 공통 처리다.
     * 호출하는 쪽의 트랜잭션 안에서 실행하며, 주문 상태와 기한 검사는 호출하는 쪽이 먼저 한다.
     *
     * @param order 완료할 주문. 호출하는 쪽 트랜잭션에서 조회한 영속 상태의 엔티티
     * @param cause 정상 완료 사유
     * @param now   거래 종료 시각. 잔액 변동 내역의 발생 시각에도 사용한다.
     */
    void complete(PurchaseOrder order, CompletionCause cause, Instant now);
}
