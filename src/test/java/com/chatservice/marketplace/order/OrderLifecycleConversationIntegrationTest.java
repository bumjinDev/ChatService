package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.conversation.realtime.ConversationSessionRegistry;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.chatservice.marketplace.support.WsTestClient;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 주문이 최종 상태가 된 뒤의 대화 쓰기 권한(BR-007, F-006-AC-03)과 읽기 전용 대화 조회(F-007-AC-01).
 * 최종 상태는 발송 전 취소로 만든다. 정상 완료·환불 완료도 같은 판단 규칙(ConversationWritability)을 쓴다.
 */
class OrderLifecycleConversationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ConversationSessionRegistry registry;

    @Test
    void 재고가_남아_있으면_한_주문이_끝나도_대화는_계속_쓰기_가능하다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 10_000, 3);
        long conversationId = startConversation(buyer, productId);
        charge(buyer, 10_000);
        long orderId = placeOrder(buyer, productId, 1, null).get("orderId").asLong();

        cancel(buyer, orderId);
        JsonNode detail = json(getJson("/api/conversations/" + conversationId, buyer));
        assertThat(detail.get("writable").asBoolean()).isTrue();
        assertThat(detail.get("orders").get(0).get("tradeStatus").asText()).isEqualTo("CANCELLED");
        sendMessage(buyer, conversationId, "다시 구매하려고 합니다.");
    }

    @Test
    void F006_AC03_판매_종료_후_해당_구매자의_주문이_모두_최종_상태가_되면_읽기_전용이다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 10_000, 2);
        long conversationId = startConversation(buyer, productId);
        long offerId = propose(buyer, conversationId, 9_000);
        sendMessage(buyer, conversationId, "구매합니다.");
        charge(buyer, 20_000);
        long first = placeOrder(buyer, productId, 1, null).get("orderId").asLong();
        long second = placeOrder(buyer, productId, 1, null).get("orderId").asLong();
        assertThat(productStatus(productId)).isEqualTo("SOLD");

        // 주문 하나가 끝나도 다른 진행 중 주문이 있으면 쓸 수 있다.
        cancel(buyer, first);
        assertThat(json(getJson("/api/conversations/" + conversationId, buyer)).get("writable").asBoolean()).isTrue();
        sendMessage(seller, conversationId, "두 번째 주문은 발송 예정입니다.");

        cancel(seller, second);
        JsonNode error = json(postJson("/api/conversations/" + conversationId + "/messages", buyer,
                Map.of("content", "추가 문의")).andExpect(status().isConflict()));
        assertThat(error.get("code").asText()).isEqualTo("CONVERSATION_READ_ONLY");

        // F-007-AC-01: 읽기 전용이 된 대화도 참여자는 과거 메시지·제안·상품 상태를 조회한다.
        JsonNode detail = json(getJson("/api/conversations/" + conversationId, seller).andExpect(status().isOk()));
        assertThat(detail.get("writable").asBoolean()).isFalse();
        assertThat(detail.get("readOnlyReason").asText()).isEqualTo("TRADE_FINALIZED");
        assertThat(detail.get("product").get("status").asText()).isEqualTo("SOLD");
        assertThat(detail.get("product").get("description").asText()).isEqualTo("상품 설명");
        assertThat(detail.get("offers").get(0).get("offerId").asLong()).isEqualTo(offerId);
        assertThat(detail.get("orders")).hasSize(2);
        JsonNode messages = json(getJson("/api/conversations/" + conversationId + "/messages", buyer));
        assertThat(messages).hasSize(2);
        // 재고를 복구하지 않으므로 읽기 전용이 다시 쓰기 가능으로 돌아가지 않는다.
        assertThat(remainingQuantity(productId)).isZero();
        assertThat(productStatus(productId)).isEqualTo("SOLD");
    }

    @Test
    void 주문이_최종_상태가_되면_그_주문_당사자의_대화에만_상태_이벤트를_보낸다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyerA = member("buyerA");
        TestMember buyerB = member("buyerB");
        long productId = registerProduct(seller, 10_000, 2);
        long conversationA = startConversation(buyerA, productId);
        long conversationB = startConversation(buyerB, productId);
        charge(buyerA, 10_000);
        charge(buyerB, 10_000);
        long orderA = placeOrder(buyerA, productId, 1, null).get("orderId").asLong();
        placeOrder(buyerB, productId, 1, null);

        WsTestClient.Connection wsA = WsTestClient.connect(port, buyerA, "?conversationId=" + conversationA);
        WsTestClient.Connection wsB = WsTestClient.connect(port, buyerB, "?conversationId=" + conversationB);
        long deadline = System.currentTimeMillis() + 5_000;
        while ((!registry.sessionsOf(conversationA).containsKey(buyerA.id())
                || !registry.sessionsOf(conversationB).containsKey(buyerB.id()))
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        try {
            cancel(buyerA, orderA);
            JsonNode event = objectMapper.readTree(wsA.next(5_000));
            assertThat(event.get("type").asText()).isEqualTo("CONVERSATION_STATE");
            assertThat(event.get("conversationId").asLong()).isEqualTo(conversationA);
            assertThat(event.get("writable").asBoolean()).isFalse();
            assertThat(event.get("readOnlyReason").asText()).isEqualTo("TRADE_FINALIZED");
            assertThat(wsB.next(300)).isNull();
        } finally {
            wsA.close();
            wsB.close();
        }
    }
}
