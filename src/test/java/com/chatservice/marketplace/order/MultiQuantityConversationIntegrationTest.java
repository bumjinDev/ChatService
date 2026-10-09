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
 * 복수 수량과 여러 주문을 고려한 대화 쓰기 권한·조회 범위(BR-007, BR-008, F-006 거절)와 실시간 재고 표시(설계 9장 복수 수량 표).
 * 주문이 최종 상태가 된 뒤의 쓰기 권한은 취소·완료 기능과 함께 OrderLifecycleConversationIntegrationTest 에서 확인한다.
 */
class MultiQuantityConversationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ConversationSessionRegistry registry;

    private WsTestClient.Connection connect(TestMember member, long conversationId) throws Exception {
        WsTestClient.Connection connection = WsTestClient.connect(port, member, "?conversationId=" + conversationId);
        long deadline = System.currentTimeMillis() + 5_000;
        while (!registry.sessionsOf(conversationId).containsKey(member.id()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        return connection;
    }

    /** CONVERSATION_STATE 가 아닌 이벤트는 건너뛰고 다음 상태 이벤트를 받는다. */
    private JsonNode nextState(WsTestClient.Connection connection) throws Exception {
        while (true) {
            String payload = connection.next(5_000);
            assertThat(payload).as("CONVERSATION_STATE 이벤트를 기다림").isNotNull();
            JsonNode event = objectMapper.readTree(payload);
            if ("CONVERSATION_STATE".equals(event.get("type").asText())) {
                return event;
            }
        }
    }

    @Test
    void 재고가_소진되면_주문이_없는_문의자는_읽기_전용이고_진행_중_주문이_있는_구매자는_쓸_수_있다() throws Exception {
        TestMember seller = member("seller");
        TestMember inquirer = member("inquirer");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 10_000, 2);
        long inquiry = startConversation(inquirer, productId);
        charge(buyer, 20_000);

        placeOrder(buyer, productId, 2, null);
        long buyerConversation = startConversation(buyer, productId);

        JsonNode inquiryDetail = json(getJson("/api/conversations/" + inquiry, inquirer));
        assertThat(inquiryDetail.get("writable").asBoolean()).isFalse();
        assertThat(inquiryDetail.get("readOnlyReason").asText()).isEqualTo("NO_ACTIVE_ORDER");
        assertThat(inquiryDetail.get("orders")).isEmpty();
        JsonNode readOnly = json(postJson("/api/conversations/" + inquiry + "/messages", inquirer,
                Map.of("content", "아직 구매 가능한가요?")).andExpect(status().isConflict()));
        assertThat(readOnly.get("code").asText()).isEqualTo("CONVERSATION_READ_ONLY");
        // 판매자도 그 대화에는 쓸 수 없다.
        postJson("/api/conversations/" + inquiry + "/messages", seller, Map.of("content", "판매 완료되었습니다."))
                .andExpect(status().isConflict());
        assertThat(count("SELECT COUNT(*) FROM CHAT_MESSAGE WHERE CONVERSATION_ID = ?", inquiry)).isZero();

        JsonNode buyerDetail = json(getJson("/api/conversations/" + buyerConversation, buyer));
        assertThat(buyerDetail.get("writable").asBoolean()).isTrue();
        assertThat(buyerDetail.get("orders")).hasSize(1);
        sendMessage(buyer, buyerConversation, "발송 일정 문의드립니다.");
        sendMessage(seller, buyerConversation, "내일 발송합니다.");

        // 판매 종료 뒤에도 권한 있는 참여자는 과거 대화와 상품 상태를 조회할 수 있다(F-007-AC-01).
        JsonNode inquiryList = json(getJson("/api/conversations", inquirer));
        assertThat(inquiryList.get(0).get("writable").asBoolean()).isFalse();
        assertThat(inquiryList.get(0).get("product").get("status").asText()).isEqualTo("SOLD");
    }

    @Test
    void 대화_상세의_주문_요약은_그_대화_구매자의_주문만_담고_다른_구매자의_주문은_노출하지_않는다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyerA = member("buyerA");
        TestMember buyerB = member("buyerB");
        long productId = registerProduct(seller, 10_000, 5);
        charge(buyerA, 50_000);
        charge(buyerB, 50_000);
        long orderA = placeOrder(buyerA, productId, 1, null).get("orderId").asLong();
        long orderB1 = placeOrder(buyerB, productId, 2, null).get("orderId").asLong();
        long orderB2 = placeOrder(buyerB, productId, 1, null).get("orderId").asLong();
        long conversationA = startConversation(buyerA, productId);
        long conversationB = startConversation(buyerB, productId);

        JsonNode ordersInA = json(getJson("/api/conversations/" + conversationA, seller)).get("orders");
        assertThat(ordersInA).hasSize(1);
        assertThat(ordersInA.get(0).get("orderId").asLong()).isEqualTo(orderA);

        JsonNode ordersInB = json(getJson("/api/conversations/" + conversationB, buyerB)).get("orders");
        assertThat(ordersInB).hasSize(2);
        assertThat(ordersInB.get(0).get("orderId").asLong()).isEqualTo(orderB2);
        assertThat(ordersInB.get(1).get("orderId").asLong()).isEqualTo(orderB1);
        // buyerA 는 buyerB 의 대화 자체에 접근할 수 없다.
        getJson("/api/conversations/" + conversationB, buyerA).andExpect(status().isForbidden());
    }

    @Test
    void 일부_구매와_마지막_수량_구매_뒤_대화_상태_이벤트와_재접속_상세가_최신_남은_수량을_보여준다() throws Exception {
        TestMember seller = member("seller");
        TestMember inquirer = member("inquirer");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 10_000, 5);
        long inquiry = startConversation(inquirer, productId);
        charge(buyer, 50_000);
        WsTestClient.Connection inquirerWs = connect(inquirer, inquiry);
        WsTestClient.Connection sellerWs = connect(seller, inquiry);
        try {
            placeOrder(buyer, productId, 2, null);
            JsonNode partial = nextState(inquirerWs);
            assertThat(partial.get("conversationId").asLong()).isEqualTo(inquiry);
            assertThat(partial.get("remainingQuantity").asLong()).isEqualTo(3);
            assertThat(partial.get("productStatus").asText()).isEqualTo("ON_SALE");
            assertThat(partial.get("writable").asBoolean()).isTrue();
            assertThat(nextState(sellerWs).get("remainingQuantity").asLong()).isEqualTo(3);
            // 다른 구매자의 주문 정보는 이벤트에 들어가지 않는다.
            assertThat(partial.has("orders")).isFalse();

            placeOrder(buyer, productId, 3, null);
            JsonNode soldOut = nextState(inquirerWs);
            assertThat(soldOut.get("remainingQuantity").asLong()).isZero();
            assertThat(soldOut.get("productStatus").asText()).isEqualTo("SOLD");
            assertThat(soldOut.get("writable").asBoolean()).isFalse();
            assertThat(soldOut.get("readOnlyReason").asText()).isEqualTo("NO_ACTIVE_ORDER");
        } finally {
            inquirerWs.close();
            sellerWs.close();
        }

        JsonNode reconnected = json(getJson("/api/conversations/" + inquiry, inquirer));
        assertThat(reconnected.get("product").get("remainingQuantity").asLong()).isZero();
        assertThat(reconnected.get("product").get("status").asText()).isEqualTo("SOLD");
        assertThat(reconnected.get("writable").asBoolean()).isFalse();
    }
}
