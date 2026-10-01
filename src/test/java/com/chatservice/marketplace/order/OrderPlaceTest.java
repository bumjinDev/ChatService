package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.common.TimeRules;
import com.chatservice.marketplace.conversation.Conversation;
import com.chatservice.marketplace.conversation.realtime.ConversationSessionRegistry;
import com.chatservice.marketplace.offer.OfferProposeRequest;
import com.chatservice.marketplace.offer.OfferResponse;
import com.chatservice.marketplace.offer.OfferStatus;
import com.chatservice.marketplace.offer.PriceOfferRepository;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductStatus;
import com.chatservice.marketplace.support.OrderTestSupport;
import com.chatservice.marketplace.support.TestTimes;
import com.chatservice.marketplace.support.WsTestClient;
import com.chatservice.marketplace.wallet.BalanceTransaction;
import com.chatservice.marketplace.wallet.TransactionType;
import com.jayway.jsonpath.JsonPath;

/** F-010 구매·잔액 결제 검증. 합의 가격 결제(F-009-AC-01, F-009-AC-02)도 함께 검증한다. */
class OrderPlaceTest extends OrderTestSupport {

	@Autowired
	private PriceOfferRepository offerRepository;

	@Autowired
	private ConversationSessionRegistry registry;

	@Test
	void F010_정상_잔액_50000원으로_등록가_30000원_상품을_결제한다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 30_000);
		charge(buyer, 50_000);

		String body = mockMvc.perform(post("/api/orders").cookie(authCookie(buyer))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":" + product.getProductId()
						+ ",\"recipientName\":\"홍길동\",\"shippingAddress\":\"서울시 중구\",\"requestId\":\"pay-1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.tradeStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.shippingStatus").value("WAITING_SHIPMENT"))
				.andExpect(jsonPath("$.paidAmount").value(30000))
				.andExpect(jsonPath("$.priceSource").value("LISTED"))
				.andExpect(jsonPath("$.myRole").value("BUYER"))
				.andExpect(jsonPath("$.recipientName").value("홍길동"))
				.andExpect(jsonPath("$.shipDeadlineAt").value(TestTimes.kst(2026, 10, 6, 0, 0).toString()))
				.andExpect(jsonPath("$.myBalanceTransactions[0].type").value("PURCHASE"))
				.andExpect(jsonPath("$.myBalanceTransactions[0].amount").value(-30000))
				.andReturn().getResponse().getContentAsString();
		Long orderId = ((Number) JsonPath.read(body, "$.orderId")).longValue();

		assertThat(balance(buyer)).isEqualTo(20_000L);
		List<BalanceTransaction> purchases = transactions(buyer, TransactionType.PURCHASE);
		assertThat(purchases).singleElement().satisfies(tx -> {
			assertThat(tx.getAmount()).isEqualTo(-30_000L);
			assertThat(tx.getBalanceAfter()).isEqualTo(20_000L);
			assertThat(tx.getOrderId()).isEqualTo(orderId);
		});
		Product sold = productRepository.findById(product.getProductId()).orElseThrow();
		assertThat(sold.getStatus()).isEqualTo(ProductStatus.SOLD);
		assertThat(sold.getSoldAt()).isEqualTo(TestTimes.BASE);

		PurchaseOrder order = orderRepository.findById(orderId).orElseThrow();
		assertThat(order.getConfirmedAt()).isEqualTo(TestTimes.BASE);
		assertThat(order.getShipDeadlineAt()).isEqualTo(new TimeRules().shipDeadlineAt(TestTimes.BASE));
		assertThat(order.getRequestId()).isEqualTo("pay-1");
		assertThat(order.getSellerId()).isEqualTo(seller);

		// 당사자 대화가 없었으므로 시스템이 만든다.
		assertThat(conversationRepository.findByProductIdOrderByConversationIdAsc(product.getProductId()))
				.singleElement().satisfies(c -> {
					assertThat(c.getBuyerId()).isEqualTo(buyer);
					assertThat(c.isCreatedBySystem()).isTrue();
				});
		// 구매·발송만으로 판매자 잔액은 늘지 않는다(BR-003).
		assertThat(balance(seller)).isZero();
	}

	@Test
	void 기존_당사자_대화가_있으면_새로_만들지_않는다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 30_000);
		Conversation existing = conversation(product, buyer);

		purchase(buyer, product);

		assertThat(conversationRepository.findByProductIdOrderByConversationIdAsc(product.getProductId()))
				.extracting(Conversation::getConversationId).containsExactly(existing.getConversationId());
	}

	@Test
	void F010_AC03_합의_40000원이_있어도_고르지_않으면_등록가_50000원이_LISTED_로_적용된다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 50_000);
		acceptedOffer(product, buyer, 40_000);
		charge(buyer, 60_000);

		OrderDetailResponse order = orderService.placeOrder(buyer, listedRequest(product));

		assertThat(order.paidAmount()).isEqualTo(50_000L);
		assertThat(order.priceSource()).isEqualTo(PriceSource.LISTED);
		assertThat(order.offerId()).isNull();
		assertThat(balance(buyer)).isEqualTo(10_000L);
	}

	@Test
	void F009_AC01_수락_제안이_있는_구매자는_offerId_로_합의_가격_결제에_성공한다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 50_000);
		OfferResponse offer = acceptedOffer(product, buyer, 40_000);
		charge(buyer, 40_000);

		OrderDetailResponse order = orderService.placeOrder(buyer, new OrderPlaceRequest(product.getProductId(),
				"홍길동", "서울", offer.offerId(), null));

		assertThat(order.paidAmount()).isEqualTo(40_000L);
		assertThat(order.priceSource()).isEqualTo(PriceSource.AGREED);
		assertThat(order.offerId()).isEqualTo(offer.offerId());
		assertThat(balance(buyer)).isZero();
	}

	@Test
	void F009_AC02_다른_구매자가_결제한_뒤_그_제안으로_결제하면_409_PRODUCT_NOT_ON_SALE_이고_제안은_PENDING() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String other = member("it_other", "다른구매자");
		Product product = product(seller, 50_000);
		Conversation conv = conversation(product, buyer);
		OfferResponse pending = offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null));
		purchase(other, product);
		charge(buyer, 50_000);

		assertThat(errorOf(() -> orderService.placeOrder(buyer, new OrderPlaceRequest(product.getProductId(),
				"홍길동", "서울", pending.offerId(), null)))).isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
		assertThat(offerRepository.findById(pending.offerId()).orElseThrow().getStatus())
				.isEqualTo(OfferStatus.PENDING);
		assertThat(balance(buyer)).isEqualTo(50_000L);
	}

	@Test
	void F010_거절_잔액_부족은_409이고_잔액_상품_주문이_바뀌지_않는다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 30_000);
		charge(buyer, 29_999);

		assertThat(errorOf(() -> orderService.placeOrder(buyer, listedRequest(product))))
				.isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
		assertUnchanged(buyer, 29_999, product);

		// 지갑이 없는 회원도 잔액 부족이다.
		String poor = member("it_poor", "잔액없음");
		assertThat(errorOf(() -> orderService.placeOrder(poor, listedRequest(product))))
				.isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
	}

	@Test
	void F010_거절_판매_종료_상품은_409() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String first = member("it_first", "먼저산사람");
		Product product = product(seller, 30_000);
		purchase(first, product);
		charge(buyer, 50_000);

		assertThat(errorOf(() -> orderService.placeOrder(buyer, listedRequest(product))))
				.isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
		assertThat(balance(buyer)).isEqualTo(50_000L);
		assertThat(orderRepository.count()).isEqualTo(1);
	}

	@Test
	void F010_거절_본인_상품은_403() {
		String seller = member("it_seller", "판매자");
		Product product = product(seller, 30_000);
		charge(seller, 50_000);

		assertThat(errorOf(() -> orderService.placeOrder(seller, listedRequest(product))))
				.isEqualTo(ErrorCode.SELF_TRADE_NOT_ALLOWED);
		assertUnchanged(seller, 50_000, product);
	}

	@Test
	void F010_거절_잘못된_offerId_는_409_INVALID_OFFER_SELECTION() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String other = member("it_other", "다른구매자");
		Product product = product(seller, 50_000);
		Product otherProduct = product(seller, 50_000);
		charge(buyer, 100_000);

		// 없는 제안
		assertThat(errorOf(() -> orderService.placeOrder(buyer,
				new OrderPlaceRequest(product.getProductId(), "홍길동", "서울", 999_999_999L, null))))
				.isEqualTo(ErrorCode.INVALID_OFFER_SELECTION);
		// 수락되지 않은 제안
		Conversation conv = conversation(product, buyer);
		OfferResponse pending = offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null));
		assertThat(errorOf(() -> orderService.placeOrder(buyer,
				new OrderPlaceRequest(product.getProductId(), "홍길동", "서울", pending.offerId(), null))))
				.isEqualTo(ErrorCode.INVALID_OFFER_SELECTION);
		// 다른 상품의 제안
		OfferResponse otherProductOffer = acceptedOffer(otherProduct, buyer, 30_000);
		assertThat(errorOf(() -> orderService.placeOrder(buyer,
				new OrderPlaceRequest(product.getProductId(), "홍길동", "서울", otherProductOffer.offerId(), null))))
				.isEqualTo(ErrorCode.INVALID_OFFER_SELECTION);
		// 다른 구매자의 제안
		OfferResponse othersOffer = acceptedOffer(product, other, 35_000);
		assertThat(errorOf(() -> orderService.placeOrder(buyer,
				new OrderPlaceRequest(product.getProductId(), "홍길동", "서울", othersOffer.offerId(), null))))
				.isEqualTo(ErrorCode.INVALID_OFFER_SELECTION);

		assertUnchanged(buyer, 100_000, product);
	}

	@Test
	void F010_거절_수령인_누락은_400() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 30_000);
		charge(buyer, 50_000);

		mockMvc.perform(post("/api/orders").cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":" + product.getProductId() + ",\"shippingAddress\":\"서울\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.recipientName").exists());
		mockMvc.perform(post("/api/orders").cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":" + product.getProductId() + ",\"recipientName\":\"홍\",\"shippingAddress\":\" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.shippingAddress").exists());
		assertUnchanged(buyer, 50_000, product);
	}

	@Test
	void 결제_API_오류_응답의_상태_코드() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 30_000);

		mockMvc.perform(post("/api/orders").cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":" + product.getProductId() + ",\"recipientName\":\"홍\",\"shippingAddress\":\"서울\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
		mockMvc.perform(post("/api/orders").cookie(authCookie(seller)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":" + product.getProductId() + ",\"recipientName\":\"홍\",\"shippingAddress\":\"서울\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SELF_TRADE_NOT_ALLOWED"));
		mockMvc.perform(post("/api/orders").cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":999999999,\"recipientName\":\"홍\",\"shippingAddress\":\"서울\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}

	@Test
	void 결제_성공_시_상품의_대화들에_CONVERSATION_STATE_를_보낸다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String other = member("it_other", "다른구매자");
		Product product = product(seller, 30_000);
		Conversation otherConv = conversation(product, other);
		Conversation partyConv = conversation(product, buyer);
		WsTestClient otherWs = WsTestClient.connect(port, "?conversationId=" + otherConv.getConversationId(),
				authCookie(other).getValue());
		WsTestClient sellerWs = WsTestClient.connect(port, "?conversationId=" + partyConv.getConversationId(),
				authCookie(seller).getValue());
		long deadline = System.currentTimeMillis() + 5000;
		while ((registry.sessionsOf(otherConv.getConversationId()).isEmpty()
				|| registry.sessionsOf(partyConv.getConversationId()).isEmpty())
				&& System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

		purchase(buyer, product);

		String toOther = otherWs.next(5000);
		assertThat((String) JsonPath.read(toOther, "$.type")).isEqualTo("CONVERSATION_STATE");
		assertThat((String) JsonPath.read(toOther, "$.productStatus")).isEqualTo("SOLD");
		assertThat((Boolean) JsonPath.read(toOther, "$.writable")).isFalse();
		assertThat((String) JsonPath.read(toOther, "$.readOnlyReason")).isEqualTo("PRODUCT_SOLD_TO_OTHER");
		String toSeller = sellerWs.next(5000);
		assertThat((Boolean) JsonPath.read(toSeller, "$.writable")).isTrue();
		otherWs.close();
		sellerWs.close();
	}

	private void assertUnchanged(String buyerId, long expectedBalance, Product product) {
		assertThat(balance(buyerId)).isEqualTo(expectedBalance);
		assertThat(transactions(buyerId, TransactionType.PURCHASE)).isEmpty();
		assertThat(productRepository.findById(product.getProductId()).orElseThrow().getStatus())
				.isEqualTo(ProductStatus.ON_SALE);
		assertThat(orderRepository.count()).isZero();
	}
}
