package com.chatservice.marketplace.order;

/** 환불 요청에 대한 판매자 판단 또는 자동 승인 결과. */
public enum RefundDecision {
	PENDING,
	APPROVED,
	REJECTED,
	AUTO_APPROVED
}
