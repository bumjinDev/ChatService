package com.chatservice.marketplace.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.conversation.Conversation;
import com.chatservice.marketplace.conversation.realtime.ConversationSessionRegistry;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestTimes;
import com.chatservice.marketplace.support.WsTestClient;
import com.jayway.jsonpath.JsonPath;

/** F-008 가격 제안 검증. */
class OfferProposeTest extends IntegrationTestSupport {

	@Autowired
	private IOfferService offerService;

	@Autowired
	private PriceOfferRepository offerRepository;

	@Autowired
	private ConversationSessionRegistry registry;

	private String offerUrl(Conversation conv) {
		return "/api/conversations/" + conv.getConversationId() + "/offers";
	}

	private ErrorCode errorOf(Runnable action) {
		try {
			action.run();
		} catch (BusinessException e) {
			return e.getErrorCode();
		}
		throw new AssertionError("BusinessException 이 발생해야 한다");
	}

	@Test
	void F008_AC01_등록가_50000원에_대기_제안이_없을_때_40000원을_제안하면_201과_PENDING() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);

		mockMvc.perform(post(offerUrl(conv)).cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":40000,\"requestId\":\"o-1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.offerId").isNumber())
				.andExpect(jsonPath("$.amount").value(40000))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.createdAt").value(TestTimes.BASE.toString()));

		assertThat(offerRepository.findAll()).singleElement().satisfies(o -> {
			assertThat(o.getBuyerId()).isEqualTo(buyer);
			assertThat(o.getSellerId()).isEqualTo(seller);
			assertThat(o.getProductId()).isEqualTo(conv.getProductId());
			assertThat(o.getRequestId()).isEqualTo("o-1");
		});
	}

	@Test
	void F008_AC02_대기_제안이_있을_때_다시_제안하면_409_OFFER_PENDING_EXISTS() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		offerService.propose(buyer, conv.getConversationId(), new OfferProposeRequest(40_000L, null));

		assertThat(errorOf(() -> offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(45_000L, null)))).isEqualTo(ErrorCode.OFFER_PENDING_EXISTS);
		assertThat(offerRepository.count()).isEqualTo(1);
	}

	@Test
	void F008_AC03_직전_제안이_거절된_뒤에는_새로_제안할_수_있다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		OfferResponse first = offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(30_000L, null));
		PriceOffer rejected = offerRepository.findById(first.offerId()).orElseThrow();
		rejected.respond(false, clock.instant());
		offerRepository.saveAndFlush(rejected);

		OfferResponse second = offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null));

		assertThat(second.status()).isEqualTo(OfferStatus.PENDING);
		assertThat(second.offerId()).isNotEqualTo(first.offerId());
		assertThat(offerRepository.findById(first.offerId()).orElseThrow().getStatus())
				.isEqualTo(OfferStatus.REJECTED);
	}

	@Test
	void F008_등록가_이상의_금액은_400() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);

		for (String amount : new String[] { "50000", "60000", "0", "-1", "100.5" }) {
			mockMvc.perform(post(offerUrl(conv)).cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"amount\":" + amount + "}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
					.andExpect(jsonPath("$.fieldErrors.amount").exists());
		}
		assertThat(offerRepository.count()).isZero();
	}

	@Test
	void F008_판매자가_제안하면_403_NOT_BUYER() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);

		mockMvc.perform(post(offerUrl(conv)).cookie(authCookie(seller)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":40000}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_BUYER"));
		assertThat(offerRepository.count()).isZero();
	}

	@Test
	void F008_수락_제안이_있으면_409_OFFER_ALREADY_ACCEPTED() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		OfferResponse first = offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null));
		PriceOffer accepted = offerRepository.findById(first.offerId()).orElseThrow();
		accepted.respond(true, clock.instant());
		offerRepository.saveAndFlush(accepted);

		mockMvc.perform(post(offerUrl(conv)).cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":35000}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OFFER_ALREADY_ACCEPTED"));
		assertThat(offerRepository.count()).isEqualTo(1);
	}

	@Test
	void 판매_종료_상품에는_409_참여자가_아니면_403() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String stranger = member("it_stranger", "제3자");
		Product product = product(seller, 50_000);
		Conversation conv = conversation(product, buyer);

		assertThat(errorOf(() -> offerService.propose(stranger, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null)))).isEqualTo(ErrorCode.NOT_CONVERSATION_MEMBER);

		markSold(product);
		assertThat(errorOf(() -> offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null)))).isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
		assertThatThrownBy(() -> offerService.propose(buyer, 999_999_999L, new OfferProposeRequest(1L, null)))
				.isInstanceOf(BusinessException.class);
		assertThat(offerRepository.count()).isZero();
	}

	@Test
	void 제안하면_판매자의_열린_세션에_OFFER_이벤트가_간다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		WsTestClient sellerWs = WsTestClient.connect(port, "?conversationId=" + conv.getConversationId(),
				authCookie(seller).getValue());
		long deadline = System.currentTimeMillis() + 5000;
		while (registry.sessionsOf(conv.getConversationId()).isEmpty() && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

		OfferResponse offer = offerService.propose(buyer, conv.getConversationId(),
				new OfferProposeRequest(40_000L, null));

		String event = sellerWs.next(5000);
		assertThat(event).isNotNull();
		assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("OFFER");
		assertThat(((Number) JsonPath.read(event, "$.offerId")).longValue()).isEqualTo(offer.offerId());
		assertThat((String) JsonPath.read(event, "$.status")).isEqualTo("PENDING");
		assertThat(((Number) JsonPath.read(event, "$.amount")).longValue()).isEqualTo(40_000L);
		sellerWs.close();
	}
}
