package com.chatservice.marketplace.product.dto;

import java.time.Instant;

import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.domain.ProductCategory;

/** 공개 목록 항목(설계 5.2.2). 설명은 목록에 넣지 않는다. */
public record ProductSummaryResponse(
        Long productId,
        String name,
        ProductCategory category,
        long price,
        long remainingQuantity,
        String sellerNickname,
        Instant createdAt) {

    public static ProductSummaryResponse of(Product product, String sellerNickname) {
        return new ProductSummaryResponse(product.getProductId(), product.getName(), product.getCategory(),
                product.getPrice(), product.getRemainingQuantity(), sellerNickname, product.getCreatedAt());
    }
}
