package com.chatservice.marketplace.conversation;

import java.time.Instant;

import com.chatservice.marketplace.common.MemberRole;
import com.chatservice.marketplace.product.ProductStatus;

/** 내 채팅 목록 항목(설계 명세서 5.2.7). */
public record ConversationSummaryResponse(
		Long conversationId,
		ProductSummary product,
		MemberRole myRole,
		String counterpartNickname,
		boolean writable,
		Instant createdAt) {

	public record ProductSummary(Long productId, String name, long price, ProductStatus status) {
	}
}
