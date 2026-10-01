package com.chatservice.marketplace.conversation;

import java.util.Optional;

import com.chatservice.marketplace.order.PurchaseOrder;
import com.chatservice.marketplace.product.Product;

/**
 * 대화를 조회한 시점의 계산 결과: 상품, 실제 당사자 주문(이 대화의 구매 희망자가 결제한 주문), 쓰기 가능 여부와 읽기 전용 사유.
 */
public record ConversationState(
		Product product,
		Optional<PurchaseOrder> partyOrder,
		boolean writable,
		ReadOnlyReason readOnlyReason) {
}
