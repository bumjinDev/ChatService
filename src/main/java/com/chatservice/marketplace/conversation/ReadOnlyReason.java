package com.chatservice.marketplace.conversation;

/** 대화가 읽기 전용인 이유. */
public enum ReadOnlyReason {
	/** 다른 구매자가 결제해 상품이 판매 종료되었다. */
	PRODUCT_SOLD_TO_OTHER,
	/** 실제 당사자의 거래가 정상 완료, 환불 완료, 취소 완료로 끝났다. */
	TRADE_FINALIZED
}
