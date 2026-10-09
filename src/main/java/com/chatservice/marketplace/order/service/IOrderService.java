package com.chatservice.marketplace.order.service;

import com.chatservice.marketplace.order.dto.PlaceOrderRequest;

public interface IOrderService {

    /**
     * F-010 구매·잔액 결제. 성공하면 생성된 주문 ID 를 돌려준다.
     * 기본 구현은 호출마다 새 결제로 처리한다(같은 결제의 재시도 식별은 후속 과제).
     */
    Long placeOrder(String buyerId, PlaceOrderRequest request);
}
