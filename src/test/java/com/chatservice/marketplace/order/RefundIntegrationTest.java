package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.order.service.IRefundService;
import com.chatservice.marketplace.order.service.IShipmentService;
import com.chatservice.marketplace.order.service.ITradeCompletionService;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * F-017 환불 요청·거래 보류, F-018 판매자 응답·무응답 자동 환불(CH-001), F-015-AC-02, F-016-AC-02(설계 9장).
 * 판매자 판단과 자동 환불이 동시에 실행되는 경우, 같은 환불 요청의 재전송 식별은 후속 과제다.
 */
class RefundIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private IShipmentService shipmentService;
    @Autowired
    private ITradeCompletionService tradeCompletionService;
    @Autowired
    private IRefundService refundService;

    private TestMember seller;
    private TestMember buyer;
    private long productId;
    private long orderId;
    private Instant deliveredAt;

    private void deliveredOrder(long price, long stock, long quantity) throws Exception {
        seller = member("seller");
        buyer = member("buyer");
        productId = registerProduct(seller, price, stock);
        charge(buyer, price * quantity);
        orderId = placeOrder(buyer, productId, quantity, null).get("orderId").asLong();
        ship(seller, orderId);
        deliveredAt = clock.instant().plus(Duration.ofMinutes(2));
        clock.setInstant(deliveredAt);
        assertThat(shipmentService.completeDueDeliveries(deliveredAt)).isEqualTo(1);
    }

    private Map<String, Object> refundBody() {
        return new HashMap<>(Map.of("reasonCode", "DESCRIPTION_MISMATCH", "detail", "설명과 달리 화면에 흠집이 있습니다."));
    }

    private JsonNode requestRefund() throws Exception {
        return json(postJson("/api/orders/" + orderId + "/refund-request", buyer, refundBody()).andExpect(status().isCreated()));
    }

    private Instant requestedAt() {
        Timestamp value = jdbcTemplate.queryForObject("SELECT REQUESTED_AT FROM REFUND_REQUEST WHERE ORDER_ID = ?",
                Timestamp.class, orderId);
        return value.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    private String decision() {
        return jdbcTemplate.queryForObject("SELECT DECISION FROM REFUND_REQUEST WHERE ORDER_ID = ?", String.class, orderId);
    }

    @Test
    void F017_AC01_배송_완료_47시간59분에_유효한_환불을_요청하면_보류되고_대금이_지급되지_않는다() throws Exception {
        deliveredOrder(30_000, 1, 1);
        Instant requested = deliveredAt.plus(Duration.ofHours(47)).plus(Duration.ofMinutes(59));
        clock.setInstant(requested);

        JsonNode held = requestRefund();
        assertThat(held.get("tradeStatus").asText()).isEqualTo("ON_HOLD");
        JsonNode refund = held.get("refundRequest");
        assertThat(refund.get("reasonCode").asText()).isEqualTo("DESCRIPTION_MISMATCH");
        assertThat(refund.get("decision").asText()).isEqualTo("PENDING");
        assertThat(Instant.parse(refund.get("requestedAt").asText())).isEqualTo(requested);
        assertThat(Instant.parse(refund.get("responseDeadlineAt").asText())).isEqualTo(requested.plus(Duration.ofHours(48)));
        assertThat(refund.get("decidedAt").isNull()).isTrue();
        assertThat(balanceOf(seller)).isZero();
        assertThat(balanceOf(buyer)).isZero();
    }

    @Test
    void F017_AC02_정확히_48시간에는_환불을_요청할_수_없다() throws Exception {
        deliveredOrder(30_000, 1, 1);
        clock.setInstant(deliveredAt.plus(Duration.ofHours(48)));
        JsonNode error = json(postJson("/api/orders/" + orderId + "/refund-request", buyer, refundBody())
                .andExpect(status().isConflict()));
        assertThat(error.get("code").asText()).isEqualTo("INSPECTION_PERIOD_ENDED");
        assertThat(tradeStatus(orderId)).isEqualTo("IN_PROGRESS");
        assertThat(count("SELECT COUNT(*) FROM REFUND_REQUEST WHERE ORDER_ID = ?", orderId)).isZero();
    }

    @Test
    void F017_AC03_접수한_뒤_다시_요청하면_409이고_최초_접수_시점이_유지된다() throws Exception {
        deliveredOrder(30_000, 1, 1);
        requestRefund();
        Instant first = requestedAt();
        clock.advance(Duration.ofHours(1));

        JsonNode again = json(postJson("/api/orders/" + orderId + "/refund-request", buyer, refundBody())
                .andExpect(status().isConflict()));
        assertThat(again.get("code").asText()).isEqualTo("REFUND_ALREADY_REQUESTED");
        assertThat(count("SELECT COUNT(*) FROM REFUND_REQUEST WHERE ORDER_ID = ?", orderId)).isEqualTo(1);
        assertThat(requestedAt()).isEqualTo(first);
    }

    @Test
    void F017_배송_완료_전_판매자_요청_잘못된_사유_설명_누락_종료된_거래는_거절된다() throws Exception {
        seller = member("seller");
        buyer = member("buyer");
        productId = registerProduct(seller, 30_000, 1);
        charge(buyer, 30_000);
        orderId = placeOrder(buyer, productId, 1, null).get("orderId").asLong();
        JsonNode notDelivered = json(postJson("/api/orders/" + orderId + "/refund-request", buyer, refundBody())
                .andExpect(status().isConflict()));
        assertThat(notDelivered.get("code").asText()).isEqualTo("ORDER_NOT_DELIVERED");

        ship(seller, orderId);
        deliveredAt = clock.instant().plus(Duration.ofMinutes(2));
        clock.setInstant(deliveredAt);
        shipmentService.completeDueDeliveries(deliveredAt);

        JsonNode bySeller = json(postJson("/api/orders/" + orderId + "/refund-request", seller, refundBody())
                .andExpect(status().isForbidden()));
        assertThat(bySeller.get("code").asText()).isEqualTo("NOT_BUYER");
        Map<String, Object> wrongReason = refundBody();
        wrongReason.put("reasonCode", "DAMAGED");
        assertThat(json(postJson("/api/orders/" + orderId + "/refund-request", buyer, wrongReason)
                .andExpect(status().isBadRequest())).get("fieldErrors").has("reasonCode")).isTrue();
        Map<String, Object> noDetail = refundBody();
        noDetail.put("detail", " ");
        postJson("/api/orders/" + orderId + "/refund-request", buyer, noDetail).andExpect(status().isBadRequest());
        assertThat(tradeStatus(orderId)).isEqualTo("IN_PROGRESS");

        postJson("/api/orders/" + orderId + "/confirm-receipt", buyer, null).andExpect(status().isOk());
        JsonNode finalized = json(postJson("/api/orders/" + orderId + "/refund-request", buyer, refundBody())
                .andExpect(status().isConflict()));
        assertThat(finalized.get("code").asText()).isEqualTo("TRADE_ALREADY_FINALIZED");
        assertThat(count("SELECT COUNT(*) FROM REFUND_REQUEST WHERE ORDER_ID = ?", orderId)).isZero();
    }

    @Test
    void F015_AC02_보류_상태에서는_정상_수령_확인과_일반_취소를_할_수_없고_대금이_지급되지_않는다() throws Exception {
        deliveredOrder(30_000, 1, 1);
        requestRefund();
        JsonNode confirm = json(postJson("/api/orders/" + orderId + "/confirm-receipt", buyer, null)
                .andExpect(status().isConflict()));
        assertThat(confirm.get("code").asText()).isEqualTo("ORDER_ON_HOLD");
        JsonNode cancel = json(postJson("/api/orders/" + orderId + "/cancel", buyer, Map.of("reason", "취소"))
                .andExpect(status().isConflict()));
        assertThat(cancel.get("code").asText()).isEqualTo("ORDER_ALREADY_SHIPPED");
        assertThat(tradeStatus(orderId)).isEqualTo("ON_HOLD");
        assertThat(balanceOf(seller)).isZero();
    }

    @Test
    void F016_AC02_47시간59분에_환불이_접수되면_48시간_자동_처리에서도_보류가_유지되고_지급되지_않는다() throws Exception {
        deliveredOrder(30_000, 1, 1);
        clock.setInstant(deliveredAt.plus(Duration.ofHours(47)).plus(Duration.ofMinutes(59)));
        requestRefund();

        assertThat(tradeCompletionService.completeExpiredInspections(deliveredAt.plus(Duration.ofHours(48)))).isZero();
        assertThat(tradeCompletionService.completeExpiredInspections(deliveredAt.plus(Duration.ofHours(60)))).isZero();
        assertThat(tradeStatus(orderId)).isEqualTo("ON_HOLD");
        assertThat(balanceOf(seller)).isZero();
        assertThat(transactionCount(seller, "SALE_PAYOUT")).isZero();
    }

    @Test
    void 보류_중에도_실제_당사자의_대화는_쓰기_가능하다() throws Exception {
        deliveredOrder(30_000, 1, 1);
        long conversationId = startConversation(buyer, productId);
        requestRefund();
        assertThat(productStatus(productId)).isEqualTo("SOLD");
        assertThat(json(getJson("/api/conversations/" + conversationId, seller)).get("writable").asBoolean()).isTrue();
        sendMessage(seller, conversationId, "사진을 보내 주실 수 있나요?");
    }

    @Test
    void F018_AC01_접수_47시간에_판매자가_동의하면_전액_반환되고_판매대금은_없다() throws Exception {
        deliveredOrder(30_000, 1, 1);
        requestRefund();
        clock.advance(Duration.ofHours(47));

        JsonNode refunded = json(postJson("/api/orders/" + orderId + "/refund-request/approve", seller, null)
                .andExpect(status().isOk()));
        assertThat(refunded.get("tradeStatus").asText()).isEqualTo("REFUNDED");
        assertThat(refunded.get("refundRequest").get("decision").asText()).isEqualTo("APPROVED");
        assertThat(refunded.get("refundRequest").get("decidedAt").isNull()).isFalse();
        assertThat(balanceOf(buyer)).isEqualTo(30_000);
        assertThat(transactionCount(buyer, "REFUND")).isEqualTo(1);
        assertThat(balanceOf(seller)).isZero();
        assertThat(transactionCount(seller, "SALE_PAYOUT")).isZero();
        // 판매자 본인의 잔액 내역에는 구매자 반환 내역이 들어가지 않는다.
        assertThat(refunded.get("myBalanceTransactions")).isEmpty();
        // 재고는 복구하지 않는다(BR-002).
        assertThat(remainingQuantity(productId)).isZero();
        assertThat(productStatus(productId)).isEqualTo("SOLD");
    }

    @Test
    void F018_AC02_접수_47시간에_판매자가_거절하면_정상_완료되고_판매대금이_지급되며_구매자_반환은_없다() throws Exception {
        deliveredOrder(30_000, 1, 1);
        requestRefund();
        clock.advance(Duration.ofHours(47));

        JsonNode completed = json(postJson("/api/orders/" + orderId + "/refund-request/reject", seller, null)
                .andExpect(status().isOk()));
        assertThat(completed.get("tradeStatus").asText()).isEqualTo("COMPLETED");
        assertThat(completed.get("completionCause").asText()).isEqualTo("REFUND_REJECTED");
        assertThat(completed.get("refundRequest").get("decision").asText()).isEqualTo("REJECTED");
        assertThat(completed.get("myBalanceTransactions").get(0).get("type").asText()).isEqualTo("SALE_PAYOUT");
        assertThat(balanceOf(seller)).isEqualTo(30_000);
        assertThat(balanceOf(buyer)).isZero();
        assertThat(transactionCount(buyer, "REFUND")).isZero();
    }

    @Test
    void F018_AC03_정확히_48시간에는_수동_응답이_거절되고_자동_환불만_유효하며_늦은_거절은_409이다() throws Exception {
        deliveredOrder(30_000, 1, 1);
        requestRefund();
        Instant responseDeadline = clock.instant().plus(Duration.ofHours(48));

        assertThat(refundService.autoApproveExpired(responseDeadline.minusSeconds(1))).isZero();
        assertThat(decision()).isEqualTo("PENDING");

        clock.setInstant(responseDeadline);
        JsonNode tooLate = json(postJson("/api/orders/" + orderId + "/refund-request/reject", seller, null)
                .andExpect(status().isConflict()));
        assertThat(tooLate.get("code").asText()).isEqualTo("REFUND_RESPONSE_DEADLINE_PASSED");

        assertThat(refundService.autoApproveExpired(responseDeadline)).isEqualTo(1);
        assertThat(decision()).isEqualTo("AUTO_APPROVED");
        assertThat(tradeStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(balanceOf(buyer)).isEqualTo(30_000);

        JsonNode lateReject = json(postJson("/api/orders/" + orderId + "/refund-request/reject", seller, null)
                .andExpect(status().isConflict()));
        assertThat(lateReject.get("code").asText()).isEqualTo("REFUND_ALREADY_DECIDED");
        assertThat(balanceOf(seller)).isZero();
        assertThat(tradeStatus(orderId)).isEqualTo("REFUNDED");
    }

    @Test
    void F018_AC04_환불_완료_뒤_자동_처리를_다시_실행해도_추가_반환이_없다() throws Exception {
        deliveredOrder(30_000, 1, 1);
        requestRefund();
        Instant responseDeadline = clock.instant().plus(Duration.ofHours(48));
        refundService.autoApproveExpired(responseDeadline);

        assertThat(refundService.autoApproveExpired(responseDeadline.plus(Duration.ofHours(1)))).isZero();
        postJson("/api/orders/" + orderId + "/refund-request/approve", seller, null).andExpect(status().isConflict());
        assertThat(transactionCount(buyer, "REFUND")).isEqualTo(1);
        assertThat(balanceOf(buyer)).isEqualTo(30_000);
    }

    @Test
    void F018_동의한_뒤_다시_응답하면_409이고_반환은_한_번이다() throws Exception {
        deliveredOrder(30_000, 1, 1);
        requestRefund();
        postJson("/api/orders/" + orderId + "/refund-request/approve", seller, null).andExpect(status().isOk());
        JsonNode again = json(postJson("/api/orders/" + orderId + "/refund-request/approve", seller, null)
                .andExpect(status().isConflict()));
        assertThat(again.get("code").asText()).isEqualTo("REFUND_ALREADY_DECIDED");
        assertThat(transactionCount(buyer, "REFUND")).isEqualTo(1);
    }

    @Test
    void F018_판매자가_아니면_403_환불_요청이_없으면_404_주문이_없으면_404이다() throws Exception {
        deliveredOrder(30_000, 1, 1);
        JsonNode noRequest = json(postJson("/api/orders/" + orderId + "/refund-request/approve", seller, null)
                .andExpect(status().isNotFound()));
        assertThat(noRequest.get("code").asText()).isEqualTo("REFUND_REQUEST_NOT_FOUND");

        requestRefund();
        JsonNode byBuyer = json(postJson("/api/orders/" + orderId + "/refund-request/approve", buyer, null)
                .andExpect(status().isForbidden()));
        assertThat(byBuyer.get("code").asText()).isEqualTo("NOT_SELLER");
        postJson("/api/orders/" + orderId + "/refund-request/reject", member("outsider"), null)
                .andExpect(status().isForbidden());
        JsonNode missing = json(postJson("/api/orders/999999999/refund-request/approve", seller, null)
                .andExpect(status().isNotFound()));
        assertThat(missing.get("code").asText()).isEqualTo("REFUND_REQUEST_NOT_FOUND");
        assertThat(decision()).isEqualTo("PENDING");
        assertThat(tradeStatus(orderId)).isEqualTo("ON_HOLD");
    }

    @Test
    void 복수_수량_주문의_전액_환불은_그_주문의_총_결제액이며_재고와_다른_주문은_바뀌지_않는다() throws Exception {
        deliveredOrder(10_000, 5, 3);
        TestMember other = member("other");
        charge(other, 20_000);
        long otherOrder = placeOrder(other, productId, 2, null).get("orderId").asLong();
        requestRefund();
        postJson("/api/orders/" + orderId + "/refund-request/approve", seller, null).andExpect(status().isOk());

        assertThat(balanceOf(buyer)).isEqualTo(30_000);
        assertThat(remainingQuantity(productId)).isZero();
        assertThat(productStatus(productId)).isEqualTo("SOLD");
        assertThat(tradeStatus(otherOrder)).isEqualTo("IN_PROGRESS");
        assertThat(balanceOf(other)).isZero();
    }
}
