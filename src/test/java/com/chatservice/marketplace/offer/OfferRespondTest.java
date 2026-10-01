package com.chatservice.marketplace.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.conversation.Conversation;
import com.chatservice.marketplace.conversation.ConversationDetailResponse;
import com.chatservice.marketplace.conversation.IConversationService;
import com.chatservice.marketplace.conversation.realtime.ConversationSessionRegistry;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductStatus;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.WsTestClient;
import com.jayway.jsonpath.JsonPath;

/** F-009 가격 제안 수락·거절 검증. */
class OfferRespondTest extends IntegrationTestSupport {

	@Autowired
	private IOfferService offerService;

	@Autowired
	private IConversationService conversationService;

	@Autowired
	private PriceOfferRepository offerRepository;

	@Autowired
	private ConversationSessionRegistry registry;

	private ErrorCode errorOf(Runnable action) {
		try {
			action.run();
		} catch (BusinessException e) {
			return e.getErrorCode();
		}
		throw new AssertionError("BusinessException 이 발생해야 한다");
	}

	@Test
	void 판매자가_수락하면_ACCEPTED_와_응답_시각이_기록되고_상품은_예약되지_않는다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 50_000);
		Conversation conv = conversation(product, buyer);
		OfferResponse offer = offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null));
		clock.advance(Duration.ofMinutes(5));

		mockMvc.perform(post("/api/offers/" + offer.offerId() + "/accept").cookie(authCookie(seller)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.respondedAt").value(clock.instant().toString()));

		Product reloaded = productRepository.findById(product.getProductId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ProductStatus.ON_SALE);
		assertThat(reloaded.getPrice()).isEqualTo(50_000L);
	}

	@Test
	void 판매자가_거절하면_REJECTED() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		OfferResponse offer = offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null));

		mockMvc.perform(post("/api/offers/" + offer.offerId() + "/reject").cookie(authCookie(seller)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.respondedAt").exists());
	}

	@Test
	void F009_AC01_수락_제안이_있는_구매자가_다시_접속하면_상세에서_ACCEPTED_를_본다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		OfferResponse offer = offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null));
		offerService.respond(seller, offer.offerId(), true);

		ConversationDetailResponse detail = conversationService.getDetail(buyer, conv.getConversationId());
		assertThat(detail.offers()).singleElement().satisfies(o -> {
			assertThat(o.offerId()).isEqualTo(offer.offerId());
			assertThat(o.status()).isEqualTo(OfferStatus.ACCEPTED);
		});
		// 합의 가격으로 결제하는 부분은 F-010 테스트(OrderPlaceTest)에서 검증한다.
	}

	@Test
	void F009_AC02_다른_구매자가_결제한_뒤_제안에_응답하면_409_PRODUCT_NOT_ON_SALE_이고_제안은_PENDING_으로_남는다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String other = member("it_other", "다른구매자");
		Product product = product(seller, 50_000);
		Conversation conv = conversation(product, buyer);
		OfferResponse offer = offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null));
		orderFixture(product, other);

		assertThat(errorOf(() -> offerService.respond(seller, offer.offerId(), true)))
				.isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
		assertThat(errorOf(() -> offerService.respond(seller, offer.offerId(), false)))
				.isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
		PriceOffer reloaded = offerRepository.findById(offer.offerId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OfferStatus.PENDING);
		assertThat(reloaded.getRespondedAt()).isNull();
	}

	@Test
	void F009_이미_응답한_제안에_다시_응답하면_409_판매자가_아니면_403() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		OfferResponse offer = offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null));

		mockMvc.perform(post("/api/offers/" + offer.offerId() + "/accept").cookie(authCookie(buyer)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_SELLER"));

		mockMvc.perform(post("/api/offers/" + offer.offerId() + "/reject").cookie(authCookie(seller)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/offers/" + offer.offerId() + "/accept").cookie(authCookie(seller)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OFFER_ALREADY_RESPONDED"));

		assertThat(offerRepository.findById(offer.offerId()).orElseThrow().getStatus())
				.isEqualTo(OfferStatus.REJECTED);
		mockMvc.perform(post("/api/offers/999999999/accept").cookie(authCookie(seller)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
	}

	@Test
	void 응답하면_구매_희망자의_열린_세션에_OFFER_이벤트가_간다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		OfferResponse offer = offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null));
		WsTestClient buyerWs = WsTestClient.connect(port, "?conversationId=" + conv.getConversationId(),
				authCookie(buyer).getValue());
		long deadline = System.currentTimeMillis() + 5000;
		while (registry.sessionsOf(conv.getConversationId()).isEmpty() && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

		offerService.respond(seller, offer.offerId(), true);

		String event = buyerWs.next(5000);
		assertThat(event).isNotNull();
		assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("OFFER");
		assertThat((String) JsonPath.read(event, "$.status")).isEqualTo("ACCEPTED");
		assertThat((String) JsonPath.read(event, "$.respondedAt")).isNotNull();
		buyerWs.close();
	}
}
