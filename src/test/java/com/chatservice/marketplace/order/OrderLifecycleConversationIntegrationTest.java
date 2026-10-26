package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.conversation.realtime.ConversationSessionRegistry;
import com.chatservice.marketplace.order.service.IShipmentService;
import com.chatservice.marketplace.order.service.ITradeCompletionService;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.chatservice.marketplace.support.WsTestClient;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 주문이 최종 상태가 된 뒤의 대화 쓰기 권한(BR-007, F-006-AC-03)과 읽기 전용 대화 조회(F-007-AC-01).
 * 읽기 전용은 정상 완료로 만든다. 발송 전 취소는 재고를 복구해 상품을 판매 중으로 되돌리므로 읽기 전용을 만들지 않는다.
 * 환불 완료도 같은 판단 규칙(ConversationWritability)을 쓴다.
 */
class OrderLifecycleConversationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ConversationSessionRegistry registry;
    @Autowired
    private IShipmentService shipmentService;
    @Autowired
    private ITradeCompletionService tradeCompletionService;

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

        // 두 주문을 발송하고 모의 배송 기간 2분이 지나 배송 완료로 만든다.
        ship(seller, first);
        ship(seller, second);
        Instant deliveredAt = clock.instant().plus(Duration.ofMinutes(2));
        clock.setInstant(deliveredAt);
        assertThat(shipmentService.completeDueDeliveries(deliveredAt)).isEqualTo(2);

        // 주문 하나가 끝나도 다른 진행 중 주문이 있으면 쓸 수 있다.
        postJson("/api/orders/" + first + "/confirm-receipt", buyer, null).andExpect(status().isOk());
        assertThat(json(getJson("/api/conversations/" + conversationId, buyer)).get("writable").asBoolean()).isTrue();
        sendMessage(seller, conversationId, "두 번째 주문도 확인해 주세요.");

        // 상품 확인 기간 48시간이 지나 두 번째 주문이 자동 완료되면 읽기 전용이 된다.
        clock.setInstant(deliveredAt.plus(Duration.ofHours(48)));
        assertThat(tradeCompletionService.completeExpiredInspections(clock.instant())).isEqualTo(1);
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
        // 정상 완료는 재고를 복구하지 않으므로 읽기 전용이 다시 쓰기 가능으로 돌아가지 않는다.
        assertThat(remainingQuantity(productId)).isZero();
        assertThat(productStatus(productId)).isEqualTo("SOLD");
    }

    @Test
    void 주문이_취소되면_그_상품의_모든_대화에_상태_이벤트를_보낸다() throws Exception {
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
            // A의 취소로 재고가 복구되어 상품이 판매 중으로 돌아오므로, A와 B의 대화에 모두 상태 이벤트가 간다.
            cancel(buyerA, orderA);
            JsonNode eventA = objectMapper.readTree(wsA.next(5_000));
            assertThat(eventA.get("type").asText()).isEqualTo("CONVERSATION_STATE");
            assertThat(eventA.get("conversationId").asLong()).isEqualTo(conversationA);
            assertThat(eventA.get("productStatus").asText()).isEqualTo("ON_SALE");
            assertThat(eventA.get("remainingQuantity").asLong()).isEqualTo(1);
            assertThat(eventA.get("writable").asBoolean()).isTrue();
            JsonNode eventB = objectMapper.readTree(wsB.next(5_000));
            assertThat(eventB.get("type").asText()).isEqualTo("CONVERSATION_STATE");
            assertThat(eventB.get("conversationId").asLong()).isEqualTo(conversationB);
            assertThat(eventB.get("productStatus").asText()).isEqualTo("ON_SALE");
            assertThat(eventB.get("writable").asBoolean()).isTrue();
        } finally {
            wsA.close();
            wsB.close();
        }
    }
}
