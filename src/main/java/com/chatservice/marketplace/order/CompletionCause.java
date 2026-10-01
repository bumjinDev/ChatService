package com.chatservice.marketplace.order;

/** 정상 완료가 된 이유. 구매자 수령 확인, 확인 기간 만료, 환불 거절 세 가지다. */
public enum CompletionCause {
	BUYER_CONFIRMED,
	AUTO_EXPIRED,
	REFUND_REJECTED
}
