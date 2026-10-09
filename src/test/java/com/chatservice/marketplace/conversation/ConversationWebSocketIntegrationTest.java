package com.chatservice.marketplace.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;

import com.chatservice.marketplace.conversation.realtime.ConversationSessionRegistry;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.chatservice.marketplace.support.WsTestClient;
import com.fasterxml.jackson.databind.JsonNode;

/** NFR-001 실시간 전달, NFR-003 WebSocket 접근 통제, 중복 접속(설계 5.3, 9장). */
class ConversationWebSocketIntegrationTest extends IntegrationTestSupport {

    private static final long WAIT_MS = 5_000;

    @Autowired
    private ConversationSessionRegistry registry;

    private WsTestClient.Connection connect(TestMember member, long conversationId) throws Exception {
        WsTestClient.Connection connection = WsTestClient.connect(port, member, "?conversationId=" + conversationId);
        awaitRegistered(conversationId, member);
        return connection;
    }

    /* 클라이언트 핸드셰이크 완료와 서버의 afterConnectionEstablished 사이 간격을 기다린다. */
    private void awaitRegistered(long conversationId, TestMember member) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (!registry.sessionsOf(conversationId).containsKey(member.id()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(registry.sessionsOf(conversationId)).containsKey(member.id());
    }

    @Test
    void NFR001_두_참여자가_연결된_상태에서_보낸_메시지를_상대방_세션이_받는다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long conversationId = startConversation(buyer, registerProduct(seller, 50_000, 1));
        WsTestClient.Connection sellerWs = connect(seller, conversationId);
        WsTestClient.Connection buyerWs = connect(buyer, conversationId);
        try {
            long messageId = sendMessage(buyer, conversationId, "실시간 문의");

            JsonNode event = objectMapper.readTree(sellerWs.next(WAIT_MS));
            assertThat(event.get("type").asText()).isEqualTo("MESSAGE");
            assertThat(event.get("messageId").asLong()).isEqualTo(messageId);
            assertThat(event.get("content").asText()).isEqualTo("실시간 문의");
            assertThat(event.get("senderNickname").asText()).isEqualTo(buyer.nickname());
            // 보낸 사람은 REST 응답으로 결과를 받으므로 MESSAGE 이벤트를 받지 않는다.
            assertThat(buyerWs.next(300)).isNull();
        } finally {
            sellerWs.close();
            buyerWs.close();
        }
    }

    @Test
    void NFR003_참여자가_아니면_핸드셰이크가_403으로_거부된다() throws Exception {
        TestMember seller = member("seller");
        long conversationId = startConversation(member("buyer"), registerProduct(seller, 50_000, 1));

        assertThatThrownBy(() -> WsTestClient.connect(port, member("outsider"), "?conversationId=" + conversationId))
                .isInstanceOf(ExecutionException.class)
                .hasStackTraceContaining("403");
        assertThatThrownBy(() -> WsTestClient.connect(port, member("outsider2"), "?conversationId=999999999"))
                .hasStackTraceContaining("403");
        assertThat(registry.sessionsOf(conversationId)).isEmpty();
    }

    @Test
    void 인증_없이_연결하면_401_conversationId_가_없거나_숫자가_아니면_400이다() throws Exception {
        TestMember seller = member("seller");
        long conversationId = startConversation(member("buyer"), registerProduct(seller, 50_000, 1));

        assertThatThrownBy(() -> WsTestClient.connect(port, null, "?conversationId=" + conversationId))
                .hasStackTraceContaining("401");
        assertThatThrownBy(() -> WsTestClient.connect(port, seller, ""))
                .hasStackTraceContaining("400");
        assertThatThrownBy(() -> WsTestClient.connect(port, seller, "?conversationId=abc"))
                .hasStackTraceContaining("400");
    }

    @Test
    void 같은_회원이_다시_연결하면_첫_연결이_3000으로_닫히고_이후_메시지는_새_연결로만_간다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long conversationId = startConversation(buyer, registerProduct(seller, 50_000, 1));
        WsTestClient.Connection first = connect(seller, conversationId);
        WsTestClient.Connection second = WsTestClient.connect(port, seller, "?conversationId=" + conversationId);
        try {
            CloseStatus closeStatus = first.awaitClose(WAIT_MS);
            assertThat(closeStatus.getCode()).isEqualTo(3000);

            sendMessage(buyer, conversationId, "두 번째 연결로 가야 하는 메시지");
            JsonNode event = objectMapper.readTree(second.next(WAIT_MS));
            assertThat(event.get("content").asText()).isEqualTo("두 번째 연결로 가야 하는 메시지");
            assertThat(first.next(300)).isNull();
            // 첫 연결의 종료 처리가 새 연결의 등록을 지우지 않는다.
            assertThat(registry.sessionsOf(conversationId)).containsKey(seller.id());
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    void 연결을_닫으면_레지스트리에서_빠지고_저장은_그대로_유지된다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long conversationId = startConversation(buyer, registerProduct(seller, 50_000, 1));
        WsTestClient.Connection sellerWs = connect(seller, conversationId);
        sellerWs.close();
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (registry.sessionsOf(conversationId).containsKey(seller.id()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(registry.sessionsOf(conversationId)).doesNotContainKey(seller.id());

        long messageId = sendMessage(buyer, conversationId, "연결이 없을 때 보낸 메시지");
        JsonNode messages = json(getJson("/api/conversations/" + conversationId + "/messages", seller));
        assertThat(messages.get(0).get("messageId").asLong()).isEqualTo(messageId);
    }
}
