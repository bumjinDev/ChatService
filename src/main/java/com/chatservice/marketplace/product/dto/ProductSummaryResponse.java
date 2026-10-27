package com.chatservice.marketplace.product.dto;

import java.time.Instant;

import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.domain.ProductCategory;

/**
 * 공개 목록 항목(설계 5.2.2). 설명은 목록에 넣지 않는다.
 * availableQuantity 는 조회한 회원 기준 구매 가능 수량이다. 남은 수량에서 다른 회원에게 걸린 활성 예약의 수량을 뺀 값이다.
 */
public record ProductSummaryResponse(
        Long productId,
        String name,
        ProductCategory category,
        long price,
        long remainingQuantity,
        long availableQuantity,
        String sellerNickname,
        Instant createdAt) {

    public static ProductSummaryResponse of(Product product, String sellerNickname, long availableQuantity) {
        return new ProductSummaryResponse(product.getProductId(), product.getName(), product.getCategory(),
                product.getPrice(), product.getRemainingQuantity(), availableQuantity, sellerNickname,
                product.getCreatedAt());
    }
}
