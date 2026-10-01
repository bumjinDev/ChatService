package com.chatservice.marketplace.order;

public interface IRefundService {

	/** 구매자가 상품 확인 기간 안에 환불을 요청해 거래를 보류시킨다(F-017). */
	OrderDetailResponse requestRefund(String memberId, Long orderId, RefundRequestCreateRequest request);
}
