package com.chatservice.marketplace.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.order.CompletionCause;
import com.chatservice.marketplace.order.PurchaseOrder;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.support.IntegrationTestSupport;

/** F-006 메시지 전송과 쓰기 가능 판단(BR-007) 검증. */
class MessageSendTest extends IntegrationTestSupport {

	@Autowired
	private IMessageService messageService;

	@Autowired
	private ChatMessageRepository messageRepository;

	@Test
	void 참여자가_보낸_메시지는_저장되고_201을_받는다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);

		mockMvc.perform(post("/api/conversations/" + conv.getConversationId() + "/messages")
				.cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"안녕하세요\",\"requestId\":\"m-1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.messageId").isNumber())
				.andExpect(jsonPath("$.senderId").value(buyer))
				.andExpect(jsonPath("$.content").value("안녕하세요"))
				.andExpect(jsonPath("$.createdAt").exists());

		assertThat(messageRepository.findByConversationIdOrderByMessageIdAsc(conv.getConversationId()))
				.singleElement().satisfies(m -> {
					assertThat(m.getSenderId()).isEqualTo(buyer);
					assertThat(m.getClientMessageId()).isEqualTo("m-1");
				});
	}

	@Test
	void F006_AC01_상대가_접속하지_않아도_메시지는_저장되어_나중에_확인된다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);

		MessageResponse sent = messageService.send(buyer, conv.getConversationId(),
				new MessageSendRequest("아직 판매하시나요?", null));

		// 상대(판매자) 세션이 없어도 저장 결과는 그대로다.
		assertThat(messageRepository.findByConversationIdOrderByMessageIdAsc(conv.getConversationId()))
				.extracting(ChatMessage::getMessageId).containsExactly(sent.messageId());
	}

	@Test
	void 판매자도_자기_상품의_대화에_보낼_수_있다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);

		MessageResponse sent = messageService.send(seller, conv.getConversationId(),
				new MessageSendRequest("네 판매 중입니다", null));
		assertThat(sent.senderNickname()).isEqualTo("판매자");
	}

	@Test
	void F006_AC03_정상_완료된_거래의_대화에_보내면_409_CONVERSATION_READ_ONLY_이고_저장되지_않는다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 50_000);
		Conversation conv = conversation(product, buyer);
		PurchaseOrder order = orderFixture(product, buyer);
		order.complete(CompletionCause.BUYER_CONFIRMED, clock.instant());
		orderRepository.saveAndFlush(order);

		assertThatThrownBy(() -> messageService.send(buyer, conv.getConversationId(),
				new MessageSendRequest("감사합니다", null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CONVERSATION_READ_ONLY);
		assertThat(messageRepository.count()).isZero();
	}

	@Test
	void 실제_당사자_대화는_진행_중과_보류_중에_쓰기_가능하다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 50_000);
		Conversation conv = conversation(product, buyer);
		PurchaseOrder order = orderFixture(product, buyer);

		messageService.send(buyer, conv.getConversationId(), new MessageSendRequest("진행 중", null));
		order.hold();
		orderRepository.saveAndFlush(order);
		messageService.send(seller, conv.getConversationId(), new MessageSendRequest("보류 중", null));

		assertThat(messageRepository.count()).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"content\":\"\"}", "{\"content\":\"   \"}", "{}" })
	void F006_빈_메시지는_400(String body) throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);

		mockMvc.perform(post("/api/conversations/" + conv.getConversationId() + "/messages")
				.cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.content").exists());
		assertThat(messageRepository.count()).isZero();
	}

	@Test
	void 천_자를_넘는_메시지는_400() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);

		mockMvc.perform(post("/api/conversations/" + conv.getConversationId() + "/messages")
				.cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"" + "가".repeat(1001) + "\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/conversations/" + conv.getConversationId() + "/messages")
				.cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"" + "가".repeat(1000) + "\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void F006_참여자가_아닌_회원은_403_NOT_CONVERSATION_MEMBER() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String stranger = member("it_stranger", "제3자");
		Conversation conv = conversation(product(seller, 50_000), buyer);

		mockMvc.perform(post("/api/conversations/" + conv.getConversationId() + "/messages")
				.cookie(authCookie(stranger)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"끼어들기\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_CONVERSATION_MEMBER"));
		assertThat(messageRepository.count()).isZero();
	}

	@Test
	void F006_다른_구매자가_결제한_뒤_비구매자_대화에_보내면_409() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String other = member("it_other", "다른구매자");
		Product product = product(seller, 50_000);
		Conversation otherConv = conversation(product, other);
		orderFixture(product, buyer);

		mockMvc.perform(post("/api/conversations/" + otherConv.getConversationId() + "/messages")
				.cookie(authCookie(other)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"아직 있나요\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONVERSATION_READ_ONLY"));
		// 판매자도 그 대화에는 보낼 수 없다.
		mockMvc.perform(post("/api/conversations/" + otherConv.getConversationId() + "/messages")
				.cookie(authCookie(seller)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"판매되었습니다\"}"))
				.andExpect(status().isConflict());
		assertThat(messageRepository.count()).isZero();
	}

	@Test
	void 없는_대화는_404() throws Exception {
		String buyer = member("it_buyer", "구매자");
		mockMvc.perform(post("/api/conversations/999999999/messages")
				.cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"안녕\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
	}
}
