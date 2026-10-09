package com.chatservice.marketplace.conversation.dto;

import java.time.Instant;

import com.chatservice.marketplace.common.PartyRole;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.domain.ProductStatus;

/** 내 채팅 목록 항목(설계 5.2.7). */
public record ConversationSummaryResponse(
        Long conversationId,
        ProductBrief product,
        PartyRole myRole,
        String counterpartNickname,
        boolean writable,
        Instant createdAt) {

    public record ProductBrief(Long productId, String name, long price, long remainingQuantity, ProductStatus status) {

        public static ProductBrief of(Product product) {
            return new ProductBrief(product.getProductId(), product.getName(), product.getPrice(),
                    product.getRemainingQuantity(), product.getStatus());
        }
    }
}
