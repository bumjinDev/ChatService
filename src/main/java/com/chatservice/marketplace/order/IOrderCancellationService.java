package com.chatservice.marketplace.order;

public interface IOrderCancellationService {

	/** 주문의 구매자 또는 판매자가 발송 전 주문을 취소한다(F-012). */
	OrderDetailResponse cancelByParty(String memberId, Long orderId, OrderCancelRequest request);
}
