package com.chatservice.marketplace.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.common.MemberRole;
import com.chatservice.marketplace.conversation.realtime.ConversationSessionRegistry;
import com.chatservice.marketplace.offer.OfferStatus;
import com.chatservice.marketplace.offer.PriceOffer;
import com.chatservice.marketplace.offer.PriceOfferRepository;
import com.chatservice.marketplace.order.PurchaseOrder;
import com.chatservice.marketplace.order.TradeStatus;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductStatus;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.WsTestClient;

/** F-007 채팅 목록·내역·거래 표시 조회와 NFR-002 재접속 복구 검증. */
class ConversationQueryTest extends IntegrationTestSupport {

	@Autowired
	private IConversationService conversationService;

	@Autowired
	private IMessageService messageService;

	@Autowired
	private PriceOfferRepository offerRepository;

	@Autowired
	private ConversationSessionRegistry registry;

	private Long send(String memberId, Conversation conv, String content) {
		clock.advance(Duration.ofSeconds(1));
		return messageService.send(memberId, conv.getConversationId(), new MessageSendRequest(content, null))
				.messageId();
	}

	private PriceOffer offer(Conversation conv, long amount) {
		clock.advance(Duration.ofSeconds(1));
		return offerRepository.saveAndFlush(PriceOffer.propose(conv.getConversationId(), conv.getProductId(),
				conv.getBuyerId(), conv.getSellerId(), amount, null, clock.instant()));
	}

	@Test
	void F007_AC01_판매_종료되어_읽기_전용이_된_대화도_참여자는_과거_메시지_제안_상품_상태를_조회한다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String other = member("it_other", "다른구매자");
		Product product = product(seller, 50_000);
		Conversation otherConv = conversation(product, other);
		send(other, otherConv, "깎아주세요");
		offer(otherConv, 40_000);
		orderFixture(product, buyer);

		ConversationDetailResponse detail = conversationService.getDetail(other, otherConv.getConversationId());
		assertThat(detail.writable()).isFalse();
		assertThat(detail.readOnlyReason()).isEqualTo(ReadOnlyReason.PRODUCT_SOLD_TO_OTHER);
		assertThat(detail.product().status()).isEqualTo(ProductStatus.SOLD);
		assertThat(detail.product().description()).isEqualTo("상품 설명");
		assertThat(detail.offers()).singleElement().satisfies(o -> {
			assertThat(o.amount()).isEqualTo(40_000L);
			assertThat(o.status()).isEqualTo(OfferStatus.PENDING);
		});
		// 다른 구매자의 주문은 이 대화에 보여 주지 않는다(BR-008).
		assertThat(detail.order()).isNull();
		assertThat(conversationService.getMessages(other, otherConv.getConversationId(), null))
				.extracting(MessageResponse::content).containsExactly("깎아주세요");

		// 판매자 쪽에서도 같은 대화는 읽기 전용으로 보인다.
		ConversationDetailResponse sellerView = conversationService.getDetail(seller, otherConv.getConversationId());
		assertThat(sellerView.myRole()).isEqualTo(MemberRole.SELLER);
		assertThat(sellerView.writable()).isFalse();
	}

	@Test
	void 실제_당사자_대화는_주문_요약을_보여주고_거래가_끝나면_TRADE_FINALIZED() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 50_000);
		Conversation conv = conversation(product, buyer);
		PurchaseOrder order = orderFixture(product, buyer);

		ConversationDetailResponse inProgress = conversationService.getDetail(buyer, conv.getConversationId());
		assertThat(inProgress.writable()).isTrue();
		assertThat(inProgress.order().orderId()).isEqualTo(order.getOrderId());
		assertThat(inProgress.order().tradeStatus()).isEqualTo(TradeStatus.IN_PROGRESS);

		order.cancel(com.chatservice.marketplace.order.CancelledBy.BUYER, "변심", clock.instant());
		orderRepository.saveAndFlush(order);
		ConversationDetailResponse finalized = conversationService.getDetail(buyer, conv.getConversationId());
		assertThat(finalized.writable()).isFalse();
		assertThat(finalized.readOnlyReason()).isEqualTo(ReadOnlyReason.TRADE_FINALIZED);
		assertThat(finalized.order().tradeStatus()).isEqualTo(TradeStatus.CANCELLED);
	}

	@Test
	void F007_AC02_연결이_끊긴_동안_제안이_수락되면_다시_접속해_상세에서_ACCEPTED_를_본다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		PriceOffer offer = offer(conv, 45_000);

		// 구매 희망자가 연결되어 있지 않은 동안 판매자가 수락한 상태를 저장소로 만든다(수락 기능은 F-009 에서 검증).
		offer.respond(true, clock.instant());
		offerRepository.saveAndFlush(offer);

		ConversationDetailResponse detail = conversationService.getDetail(buyer, conv.getConversationId());
		assertThat(detail.offers()).singleElement().satisfies(o -> {
			assertThat(o.offerId()).isEqualTo(offer.getOfferId());
			assertThat(o.status()).isEqualTo(OfferStatus.ACCEPTED);
			assertThat(o.respondedAt()).isNotNull();
		});
	}

	@Test
	void F007_순서_메시지_5건은_messageId_오름차순이고_afterId_는_뒤의_2건만_돌려준다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		List<Long> ids = new ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			ids.add(send(i % 2 == 0 ? seller : buyer, conv, "메시지" + i));
		}

		List<MessageResponse> all = conversationService.getMessages(buyer, conv.getConversationId(), null);
		assertThat(all).extracting(MessageResponse::messageId).containsExactlyElementsOf(ids);
		assertThat(all).extracting(MessageResponse::content)
				.containsExactly("메시지1", "메시지2", "메시지3", "메시지4", "메시지5");

		List<MessageResponse> after = conversationService.getMessages(buyer, conv.getConversationId(), ids.get(2));
		assertThat(after).extracting(MessageResponse::content).containsExactly("메시지4", "메시지5");
	}

	@Test
	void NFR002_연결이_끊긴_동안_저장된_3건을_afterId_로_다시_가져온다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		Long lastSeen = send(seller, conv, "연결 중 받은 메시지");

		// 구매 희망자는 연결이 끊긴 상태다. 그동안 판매자가 3건을 보낸다.
		send(seller, conv, "놓친1");
		send(seller, conv, "놓친2");
		send(seller, conv, "놓친3");

		String body = mockMvc.perform(get("/api/conversations/" + conv.getConversationId() + "/messages")
				.param("afterId", String.valueOf(lastSeen)).cookie(authCookie(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].content").value("놓친1"))
				.andExpect(jsonPath("$[2].content").value("놓친3"))
				.andExpect(jsonPath("$[0].senderNickname").value("판매자"))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).contains("senderId", "createdAt", "messageId");
	}

	@Test
	void F006_AC01_저장_후_상대가_다시_접속하면_조회로_저장된_메시지를_받는다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		send(buyer, conv, "접속 전에 보낸 메시지");

		// 판매자가 나중에 접속한다. 이미 저장된 메시지는 실시간으로 다시 보내지 않으며 조회로 확인한다.
		WsTestClient sellerWs = WsTestClient.connect(port, "?conversationId=" + conv.getConversationId(),
				authCookie(seller).getValue());
		long deadline = System.currentTimeMillis() + 5000;
		while (registry.sessionsOf(conv.getConversationId()).isEmpty() && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		assertThat(sellerWs.next(300)).isNull();

		mockMvc.perform(get("/api/conversations/" + conv.getConversationId() + "/messages")
				.cookie(authCookie(seller)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].content").value("접속 전에 보낸 메시지"));
		sellerWs.close();
	}

	@Test
	void 내_채팅_목록은_참여한_대화만_최신순으로_역할과_상대_닉네임을_보여준다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String stranger = member("it_stranger", "제3자");
		Product p1 = product(seller, 10_000);
		Product p2 = product(buyer, 20_000);
		Product p3 = product(stranger, 30_000);
		Conversation c1 = conversation(p1, buyer);
		clock.advance(Duration.ofMinutes(1));
		Conversation c2 = conversation(p2, seller);
		clock.advance(Duration.ofMinutes(1));
		conversation(p3, seller);

		mockMvc.perform(get("/api/conversations").cookie(authCookie(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].conversationId").value(c2.getConversationId()))
				.andExpect(jsonPath("$[0].myRole").value("SELLER"))
				.andExpect(jsonPath("$[0].counterpartNickname").value("판매자"))
				.andExpect(jsonPath("$[0].product.productId").value(p2.getProductId()))
				.andExpect(jsonPath("$[0].product.price").value(20000))
				.andExpect(jsonPath("$[0].product.status").value("ON_SALE"))
				.andExpect(jsonPath("$[0].writable").value(true))
				.andExpect(jsonPath("$[0].createdAt").exists())
				.andExpect(jsonPath("$[1].conversationId").value(c1.getConversationId()))
				.andExpect(jsonPath("$[1].myRole").value("BUYER"));
	}

	@Test
	void NFR003_참여자가_아닌_회원은_상세와_메시지를_조회할_수_없다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String stranger = member("it_stranger", "제3자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		send(buyer, conv, "비공개");

		mockMvc.perform(get("/api/conversations/" + conv.getConversationId()).cookie(authCookie(stranger)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_CONVERSATION_MEMBER"));
		mockMvc.perform(get("/api/conversations/" + conv.getConversationId() + "/messages")
				.cookie(authCookie(stranger)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.content").doesNotExist());
		mockMvc.perform(get("/api/conversations/999999999").cookie(authCookie(stranger)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
	}

	@Test
	void 반복_조회는_상태를_바꾸지_않는다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		send(buyer, conv, "한 번");

		for (int i = 0; i < 3; i++) {
			conversationService.getDetail(buyer, conv.getConversationId());
			conversationService.getMessages(buyer, conv.getConversationId(), null);
		}
		assertThat(conversationService.getMessages(buyer, conv.getConversationId(), null)).hasSize(1);
		assertThat(conversationRepository.count()).isEqualTo(1);
	}
}
