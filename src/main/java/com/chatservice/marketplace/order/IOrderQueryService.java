package com.chatservice.marketplace.order;

import java.util.List;

public interface IOrderQueryService {

	/** 요청한 회원이 구매자인 주문 목록. 구매 확정 시각 내림차순이다(F-019). */
	List<OrderSummaryResponse> listPurchases(String memberId);

	/** 요청한 회원이 판매자인 주문 목록. 구매 확정 시각 내림차순이다(F-019). */
	List<OrderSummaryResponse> listSales(String memberId);

	/** 주문 상세. 당사자만 조회할 수 있다(F-019, NFR-003). */
	OrderDetailResponse getDetail(String memberId, Long orderId);
}
