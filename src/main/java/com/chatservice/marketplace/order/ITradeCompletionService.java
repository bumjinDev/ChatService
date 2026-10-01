package com.chatservice.marketplace.order;

public interface ITradeCompletionService {

	/** 구매자가 정상 수령을 확인해 거래를 완료하고 판매대금을 지급한다(F-015). */
	OrderDetailResponse confirmReceipt(String memberId, Long orderId);
}
