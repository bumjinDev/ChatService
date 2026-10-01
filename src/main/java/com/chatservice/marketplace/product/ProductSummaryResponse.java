package com.chatservice.marketplace.product;

import java.time.Instant;

/** 상품 목록 항목(설계 명세서 5.2.2). 설명은 목록에 넣지 않는다. */
public record ProductSummaryResponse(
		Long productId,
		String name,
		Category category,
		long price,
		String sellerNickname,
		Instant createdAt) {

	public static ProductSummaryResponse of(Product product, String sellerNickname) {
		return new ProductSummaryResponse(product.getProductId(), product.getName(), product.getCategory(),
				product.getPrice(), sellerNickname, product.getCreatedAt());
	}
}
