package com.chatservice.marketplace.order;

/** 주문과 배송의 진행 상태. 발송 대기 → 배송 중 → 배송 완료 순서로만 바뀐다. */
public enum ShippingStatus {
	WAITING_SHIPMENT,
	SHIPPING,
	DELIVERED
}
