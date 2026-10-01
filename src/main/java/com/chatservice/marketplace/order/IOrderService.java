package com.chatservice.marketplace.order;

public interface IOrderService {

	/** 잔액으로 상품을 결제해 주문을 만든다(F-010). */
	OrderDetailResponse placeOrder(String buyerId, OrderPlaceRequest request);
}
