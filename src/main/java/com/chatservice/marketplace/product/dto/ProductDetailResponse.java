package com.chatservice.marketplace.product.dto;

import java.time.Instant;

import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.domain.ProductCategory;
import com.chatservice.marketplace.product.domain.ProductStatus;

/**
 * 상품 상세(설계 5.2.3). 등록 응답(5.2.1)도 같은 형식이다.
 * availableQuantity 는 조회한 회원 기준 구매 가능 수량이다. 남은 수량에서 다른 회원에게 걸린 활성 예약의 수량을 뺀 값이며, 등록 응답에서는 남은 수량과 같다.
 */
public record ProductDetailResponse(
        Long productId,
        String name,
        String description,
        ProductCategory category,
        long price,
        long initialQuantity,
        long remainingQuantity,
        long availableQuantity,
        ProductStatus status,
        String sellerNickname,
        Instant createdAt) {

    public static ProductDetailResponse of(Product product, String sellerNickname, long availableQuantity) {
        return new ProductDetailResponse(product.getProductId(), product.getName(), product.getDescription(),
                product.getCategory(), product.getPrice(), product.getInitialQuantity(),
                product.getRemainingQuantity(), availableQuantity, product.getStatus(), sellerNickname,
                product.getCreatedAt());
    }
}
