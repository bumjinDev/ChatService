package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.conversation.ConversationDetailResponse;
import com.chatservice.marketplace.conversation.IConversationService;
import com.chatservice.marketplace.conversation.ReadOnlyReason;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductStatus;
import com.chatservice.marketplace.support.OrderTestSupport;
import com.chatservice.marketplace.wallet.TransactionType;

/** F-012 발송 전 주문 취소 검증. */
class OrderCancelTest extends OrderTestSupport {

	@Autowired
	private IOrderCancellationService cancellationService;

	@Autowired
	private IShipmentService shipmentService;

	@Autowired
	private IConversationService conversationService;

	@Test
	void F012_AC01_발송_대기_주문을_구매자가_사유를_넣어_취소하면_CANCELLED_와_전액_반환() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 30_000);
		OrderDetailResponse order = purchase(buyer, product);
		assertThat(balance(buyer)).isZero();
		clock.advance(Duration.ofHours(1));

		mockMvc.perform(post("/api/orders/" + order.orderId() + "/cancel").cookie(authCookie(buyer))
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"단순 변심\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tradeStatus").value("CANCELLED"))
				.andExpect(jsonPath("$.shippingStatus").value("WAITING_SHIPMENT"))
				.andExpect(jsonPath("$.cancellation.cancelledBy").value("BUYER"))
				.andExpect(jsonPath("$.cancellation.reason").value("단순 변심"))
				.andExpect(jsonPath("$.cancellation.cancelledAt").value(clock.instant().toString()))
				.andExpect(jsonPath("$.finalizedAt").value(clock.instant().toString()))
				.andExpect(jsonPath("$.myBalanceTransactions[1].type").value("CANCEL_REFUND"))
				.andExpect(jsonPath("$.myBalanceTransactions[1].amount").value(30000));

		assertThat(balance(buyer)).isEqualTo(30_000L);
		assertThat(transactions(buyer, TransactionType.CANCEL_REFUND)).singleElement()
				.satisfies(tx -> assertThat(tx.getOrderId()).isEqualTo(order.orderId()));
		assertThat(balance(seller)).isZero();
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).isEmpty();
		// 상품은 판매 종료로 남는다(BR-002).
		assertThat(productRepository.findById(product.getProductId()).orElseThrow().getStatus())
				.isEqualTo(ProductStatus.SOLD);
		// 실제 당사자 대화는 읽기 전용이 된다(BR-007).
		Long convId = conversationRepository.findByProductIdOrderByConversationIdAsc(product.getProductId()).get(0)
				.getConversationId();
		ConversationDetailResponse conv = conversationService.getDetail(buyer, convId);
		assertThat(conv.writable()).isFalse();
		assertThat(conv.readOnlyReason()).isEqualTo(ReadOnlyReason.TRADE_FINALIZED);
	}

	@Test
	void 판매자도_발송_전에_취소할_수_있고_반환은_구매자에게_간다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = purchase(buyer, product(seller, 30_000));

		OrderDetailResponse cancelled = cancellationService.cancelByParty(seller, order.orderId(),
				new OrderCancelRequest("재고 문제"));

		assertThat(cancelled.cancellation().cancelledBy()).isEqualTo(CancelledBy.SELLER);
		assertThat(cancelled.myBalanceTransactions()).isEmpty();
		assertThat(balance(buyer)).isEqualTo(30_000L);
		assertThat(balance(seller)).isZero();
	}

	@Test
	void F012_AC02_배송_중_주문을_취소하면_409_ORDER_ALREADY_SHIPPED() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = purchase(buyer, product(seller, 30_000));
		shipmentService.registerShipment(seller, order.orderId(), new ShipmentRegisterRequest("우체국", "1", null));

		assertThat(errorOf(() -> cancellationService.cancelByParty(buyer, order.orderId(),
				new OrderCancelRequest("변심")))).isEqualTo(ErrorCode.ORDER_ALREADY_SHIPPED);
		PurchaseOrder reloaded = orderRepository.findById(order.orderId()).orElseThrow();
		assertThat(reloaded.getTradeStatus()).isEqualTo(TradeStatus.IN_PROGRESS);
		assertThat(balance(buyer)).isZero();
	}

	@Test
	void F012_AC03_순차_취소한_뒤_다시_취소하면_409_TRADE_ALREADY_FINALIZED_이고_반환은_1건() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = purchase(buyer, product(seller, 30_000));
		cancellationService.cancelByParty(buyer, order.orderId(), new OrderCancelRequest("변심"));

		assertThat(errorOf(() -> cancellationService.cancelByParty(buyer, order.orderId(),
				new OrderCancelRequest("다시")))).isEqualTo(ErrorCode.TRADE_ALREADY_FINALIZED);
		assertThat(errorOf(() -> cancellationService.cancelByParty(seller, order.orderId(),
				new OrderCancelRequest("다시")))).isEqualTo(ErrorCode.TRADE_ALREADY_FINALIZED);
		assertThat(transactions(buyer, TransactionType.CANCEL_REFUND)).hasSize(1);
		assertThat(balance(buyer)).isEqualTo(30_000L);
	}

	@Test
	void 당사자가_아니면_403_사유가_없으면_400() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String stranger = member("it_stranger", "제3자");
		OrderDetailResponse order = purchase(buyer, product(seller, 30_000));

		mockMvc.perform(post("/api/orders/" + order.orderId() + "/cancel").cookie(authCookie(stranger))
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"남의 주문\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_TRADE_PARTY"));
		mockMvc.perform(post("/api/orders/" + order.orderId() + "/cancel").cookie(authCookie(buyer))
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.reason").exists());
		mockMvc.perform(post("/api/orders/" + order.orderId() + "/cancel").cookie(authCookie(buyer))
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "가".repeat(501) + "\"}"))
				.andExpect(status().isBadRequest());

		assertThat(orderRepository.findById(order.orderId()).orElseThrow().getTradeStatus())
				.isEqualTo(TradeStatus.IN_PROGRESS);
		assertThat(transactions(buyer, TransactionType.CANCEL_REFUND)).isEmpty();
	}
}
