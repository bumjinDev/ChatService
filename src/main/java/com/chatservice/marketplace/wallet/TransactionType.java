package com.chatservice.marketplace.wallet;

/** 잔액 변동 유형(F-004): 충전, 구매, 취소 반환, 환불 반환, 판매대금 지급. */
public enum TransactionType {
	CHARGE,
	PURCHASE,
	CANCEL_REFUND,
	REFUND,
	SALE_PAYOUT
}
