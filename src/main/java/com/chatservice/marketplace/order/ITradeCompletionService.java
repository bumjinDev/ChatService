package com.chatservice.marketplace.order;

import java.time.Instant;

public interface ITradeCompletionService {

	/** 구매자가 정상 수령을 확인해 거래를 완료하고 판매대금을 지급한다(F-015). */
	OrderDetailResponse confirmReceipt(String memberId, Long orderId);

	/**
	 * 상품 확인 기간이 끝난 배송 완료 주문을 자동으로 정상 완료하고 판매대금을 지급한다(F-016).
	 * 주문 한 건씩 별도 트랜잭션으로 처리하고 처리 건수를 돌려준다.
	 */
	int completeExpiredInspections(Instant now);

	/**
	 * 공통 완료 처리. 거래를 COMPLETED 로 바꾸고 판매자에게 판매대금을 지급한다. 호출한 쪽의 트랜잭션에 참여한다.
	 * 판매자의 환불 거절(F-018)에서도 호출한다. actorId 가 null 이면 시스템 처리다.
	 */
	void complete(PurchaseOrder order, CompletionCause cause, Instant now, String actorId);
}
