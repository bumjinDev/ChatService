package com.chatservice.marketplace.conversation;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.order.PurchaseOrder;
import com.chatservice.marketplace.order.PurchaseOrderRepository;
import com.chatservice.marketplace.order.TradeStatus;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductRepository;

/**
 * 대화의 쓰기 가능 여부를 상품과 주문 상태로 계산한다(BR-007, 설계 명세서 6.6절).
 * 1. 상품이 판매 중이면 쓰기 가능
 * 2. 판매 종료이고 주문의 구매자가 대화의 구매 희망자와 다르면 읽기 전용(PRODUCT_SOLD_TO_OTHER)
 * 3. 실제 당사자 주문이 진행 중 또는 보류이면 쓰기 가능
 * 4. 실제 당사자 주문이 정상 완료, 환불 완료, 취소 완료이면 읽기 전용(TRADE_FINALIZED)
 */
@Component
public class ConversationStateEvaluator {

	private final ProductRepository productRepository;
	private final PurchaseOrderRepository orderRepository;

	public ConversationStateEvaluator(ProductRepository productRepository, PurchaseOrderRepository orderRepository) {
		this.productRepository = productRepository;
		this.orderRepository = orderRepository;
	}

	public ConversationState evaluate(Conversation conversation) {
		Product product = productRepository.findById(conversation.getProductId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		return evaluate(conversation, product);
	}

	public ConversationState evaluate(Conversation conversation, Product product) {
		if (product.isOnSale()) {
			return new ConversationState(product, Optional.empty(), true, null);
		}
		Optional<PurchaseOrder> order = orderRepository.findFirstByProductIdOrderByOrderIdAsc(product.getProductId());
		if (order.isEmpty() || !order.get().getBuyerId().equals(conversation.getBuyerId())) {
			return new ConversationState(product, Optional.empty(), false, ReadOnlyReason.PRODUCT_SOLD_TO_OTHER);
		}
		TradeStatus tradeStatus = order.get().getTradeStatus();
		if (tradeStatus.isFinal()) {
			return new ConversationState(product, order, false, ReadOnlyReason.TRADE_FINALIZED);
		}
		return new ConversationState(product, order, true, null);
	}
}
