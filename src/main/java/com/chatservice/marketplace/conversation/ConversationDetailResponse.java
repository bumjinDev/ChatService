package com.chatservice.marketplace.conversation;

import java.util.List;

import com.chatservice.marketplace.common.MemberRole;
import com.chatservice.marketplace.offer.OfferResponse;
import com.chatservice.marketplace.order.ShippingStatus;
import com.chatservice.marketplace.order.TradeStatus;
import com.chatservice.marketplace.product.Category;
import com.chatservice.marketplace.product.ProductStatus;

/** 대화 상세·거래 표시 응답(설계 명세서 5.2.8). */
public record ConversationDetailResponse(
		Long conversationId,
		ProductView product,
		Participant buyer,
		Participant seller,
		MemberRole myRole,
		boolean writable,
		ReadOnlyReason readOnlyReason,
		List<OfferResponse> offers,
		OrderSummary order) {

	public record ProductView(Long productId, String name, String description, Category category, long price,
			ProductStatus status) {
	}

	public record Participant(String nickname) {
	}

	public record OrderSummary(Long orderId, TradeStatus tradeStatus, ShippingStatus shippingStatus) {
	}
}
