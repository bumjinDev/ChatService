package com.chatservice.marketplace.order;

import java.time.Instant;

public interface IOrderCancellationService {

	/** 주문의 구매자 또는 판매자가 발송 전 주문을 취소한다(F-012). */
	OrderDetailResponse cancelByParty(String memberId, Long orderId, OrderCancelRequest request);

	/**
	 * 발송 기한이 지난 미발송 주문을 자동 취소한다(F-013). 주문 한 건씩 별도 트랜잭션으로 처리하고 처리 건수를 돌려준다.
	 */
	int cancelExpiredUnshipped(Instant now);
}
