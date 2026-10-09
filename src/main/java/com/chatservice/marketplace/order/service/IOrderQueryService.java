package com.chatservice.marketplace.order.service;

import java.util.List;

import com.chatservice.marketplace.order.dto.OrderDetailResponse;
import com.chatservice.marketplace.order.dto.OrderSummaryResponse;

public interface IOrderQueryService {

    /** 주문 상세. 없으면 404 ORDER_NOT_FOUND, 당사자가 아니면 403 NOT_TRADE_PARTY. */
    OrderDetailResponse getDetail(String memberId, Long orderId);

    /** F-019 구매 목록: 요청한 회원이 구매자인 주문(구매 확정 시각 내림차순). */
    List<OrderSummaryResponse> purchases(String memberId);

    /** F-019 판매 목록: 요청한 회원이 판매자인 주문(구매 확정 시각 내림차순). */
    List<OrderSummaryResponse> sales(String memberId);
}
