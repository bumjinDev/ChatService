package com.chatservice.marketplace.product.dto;

import java.time.Instant;

import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.domain.ProductCategory;
import com.chatservice.marketplace.product.domain.ProductStatus;

/** 상품 상세(설계 5.2.3). 등록 응답(5.2.1)도 같은 형식이다. */
public record ProductDetailResponse(
        Long productId,
        String name,
        String description,
        ProductCategory category,
        long price,
        long initialQuantity,
        long remainingQuantity,
        ProductStatus status,
        String sellerNickname,
        Instant createdAt) {

    public static ProductDetailResponse of(Product product, String sellerNickname) {
        return new ProductDetailResponse(product.getProductId(), product.getName(), product.getDescription(),
                product.getCategory(), product.getPrice(), product.getInitialQuantity(),
                product.getRemainingQuantity(), product.getStatus(), sellerNickname, product.getCreatedAt());
    }
}
