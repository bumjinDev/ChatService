package com.chatservice.marketplace.order;

/** 거래 결과 상태. COMPLETED, REFUNDED, CANCELLED 는 최종 상태다(BR-005). */
public enum TradeStatus {
	IN_PROGRESS,
	ON_HOLD,
	COMPLETED,
	REFUNDED,
	CANCELLED;

	/** 정상 완료, 환불 완료, 취소 완료 중 하나인지. */
	public boolean isFinal() {
		return this == COMPLETED || this == REFUNDED || this == CANCELLED;
	}
}
