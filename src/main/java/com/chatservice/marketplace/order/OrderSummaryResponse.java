package com.chatservice.marketplace.order;

import java.time.Instant;

/** 구매·판매 목록 항목(설계 명세서 5.2.14). 수령인과 주소는 넣지 않는다. */
public record OrderSummaryResponse(
		Long orderId,
		ProductSummary product,
		long paidAmount,
		PriceSource priceSource,
		ShippingStatus shippingStatus,
		TradeStatus tradeStatus,
		Instant confirmedAt,
		Instant finalizedAt) {

	public record ProductSummary(Long productId, String name) {
	}
}
