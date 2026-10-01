package com.chatservice.marketplace.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.socket.CloseStatus;

import com.chatservice.marketplace.conversation.realtime.ConversationSessionRegistry;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.WsTestClient;
import com.jayway.jsonpath.JsonPath;

/** F-006 실시간 전달(NFR-001), 연결 권한(NFR-003 일부), 중복 접속 검증. */
class ConversationWebSocketTest extends IntegrationTestSupport {

	@Autowired
	private IMessageService messageService;

	@Autowired
	private ConversationSessionRegistry registry;

	private String jwt(String memberId) {
		return authCookie(memberId).getValue();
	}

	private void awaitRegistered(Long conversationId, int count) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5000;
		while (registry.sessionsOf(conversationId).size() < count && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		assertThat(registry.sessionsOf(conversationId)).hasSize(count);
	}

	@Test
	void NFR001_두_참여자가_연결된_상태에서_한쪽이_보내면_다른쪽이_MESSAGE_를_받는다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		String query = "?conversationId=" + conv.getConversationId();

		WsTestClient buyerWs = WsTestClient.connect(port, query, jwt(buyer));
		WsTestClient sellerWs = WsTestClient.connect(port, query, jwt(seller));
		awaitRegistered(conv.getConversationId(), 2);

		String response = mockMvc.perform(post("/api/conversations/" + conv.getConversationId() + "/messages")
				.cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"실시간 확인\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Number messageId = JsonPath.read(response, "$.messageId");

		String event = sellerWs.next(5000);
		assertThat(event).isNotNull();
		assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("MESSAGE");
		assertThat(((Number) JsonPath.read(event, "$.messageId")).longValue()).isEqualTo(messageId.longValue());
		assertThat((String) JsonPath.read(event, "$.content")).isEqualTo("실시간 확인");
		assertThat((String) JsonPath.read(event, "$.senderId")).isEqualTo(buyer);
		assertThat((String) JsonPath.read(event, "$.senderNickname")).isEqualTo("구매자");
		assertThat(((Number) JsonPath.read(event, "$.conversationId")).longValue())
				.isEqualTo(conv.getConversationId());
		// 보낸 사람은 REST 응답으로 결과를 받으므로 이벤트를 받지 않는다.
		assertThat(buyerWs.next(300)).isNull();

		buyerWs.close();
		sellerWs.close();
	}

	@Test
	void 중복_접속_두번째_연결이_첫_연결을_3000으로_닫고_이후_메시지는_두번째로만_간다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		String query = "?conversationId=" + conv.getConversationId();

		WsTestClient first = WsTestClient.connect(port, query, jwt(seller));
		awaitRegistered(conv.getConversationId(), 1);
		WsTestClient second = WsTestClient.connect(port, query, jwt(seller));

		CloseStatus closed = first.awaitClose(5000);
		assertThat(closed.getCode()).isEqualTo(3000);
		awaitRegistered(conv.getConversationId(), 1);

		messageService.send(buyer, conv.getConversationId(), new MessageSendRequest("두번째로", null));

		String event = second.next(5000);
		assertThat(event).isNotNull();
		assertThat((String) JsonPath.read(event, "$.content")).isEqualTo("두번째로");
		assertThat(first.next(300)).isNull();
		assertThat(second.isOpen()).isTrue();

		second.close();
	}

	@Test
	void 클라이언트가_보낸_텍스트_프레임은_무시되고_연결은_유지된다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);
		String query = "?conversationId=" + conv.getConversationId();

		WsTestClient buyerWs = WsTestClient.connect(port, query, jwt(buyer));
		WsTestClient sellerWs = WsTestClient.connect(port, query, jwt(seller));
		awaitRegistered(conv.getConversationId(), 2);

		buyerWs.sendText("무시될 프레임");
		assertThat(sellerWs.next(500)).isNull();
		assertThat(buyerWs.isOpen()).isTrue();

		buyerWs.close();
		sellerWs.close();
	}

	@Test
	void NFR003_참여자가_아닌_회원의_연결은_403으로_거부된다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String stranger = member("it_stranger", "제3자");
		Conversation conv = conversation(product(seller, 50_000), buyer);

		assertThatThrownBy(() -> WsTestClient.connect(port, "?conversationId=" + conv.getConversationId(),
				jwt(stranger)))
				.hasStackTraceContaining("403");
		TimeUnit.MILLISECONDS.sleep(100);
		assertThat(registry.sessionsOf(conv.getConversationId())).isEmpty();
	}

	@Test
	void 인증이_없으면_401_conversationId_가_없거나_숫자가_아니면_400() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Conversation conv = conversation(product(seller, 50_000), buyer);

		assertThatThrownBy(() -> WsTestClient.connect(port, "?conversationId=" + conv.getConversationId(), null))
				.hasStackTraceContaining("401");
		assertThatThrownBy(() -> WsTestClient.connect(port, "", jwt(buyer)))
				.hasStackTraceContaining("400");
		assertThatThrownBy(() -> WsTestClient.connect(port, "?conversationId=abc", jwt(buyer)))
				.hasStackTraceContaining("400");
	}
}
