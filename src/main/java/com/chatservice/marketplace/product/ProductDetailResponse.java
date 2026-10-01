package com.chatservice.marketplace.product;

import java.time.Instant;

/** 상품 상세 응답(설계 명세서 5.2.3). */
public record ProductDetailResponse(
		Long productId,
		String name,
		String description,
		Category category,
		long price,
		ProductStatus status,
		String sellerNickname,
		Instant createdAt) {

	public static ProductDetailResponse of(Product product, String sellerNickname) {
		return new ProductDetailResponse(product.getProductId(), product.getName(), product.getDescription(),
				product.getCategory(), product.getPrice(), product.getStatus(), sellerNickname,
				product.getCreatedAt());
	}
}
