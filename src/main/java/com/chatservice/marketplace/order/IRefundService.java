package com.chatservice.marketplace.order;

import java.time.Instant;

public interface IRefundService {

	/** 구매자가 상품 확인 기간 안에 환불을 요청해 거래를 보류시킨다(F-017). */
	OrderDetailResponse requestRefund(String memberId, Long orderId, RefundRequestCreateRequest request);

	/** 판매자가 응답 기한 안에 환불에 동의(approve=true)하거나 거절한다(F-018). */
	OrderDetailResponse decide(String memberId, Long orderId, boolean approve);

	/**
	 * 응답 기한이 지난 응답 대기 환불 요청을 자동 승인해 전액 환불한다(F-018).
	 * 요청 한 건씩 별도 트랜잭션으로 처리하고 처리 건수를 돌려준다.
	 */
	int autoApproveExpired(Instant now);
}
