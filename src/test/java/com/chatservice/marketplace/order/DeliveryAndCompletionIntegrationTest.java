package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.order.service.IShipmentService;
import com.chatservice.marketplace.order.service.ITradeCompletionService;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * F-014 모의 배송 완료, F-015 정상 수령 확인·판매대금 지급, F-016 상품 확인 기간 만료 자동 완료(설계 9장).
 * 보류 상태와의 관계(F-015-AC-02, F-016-AC-02)는 환불 요청 기능과 함께 RefundIntegrationTest 에서 확인한다.
 * 수령 확인·자동 완료·환불 요청이 동시에 들어오는 경우는 후속 과제다.
 */
class DeliveryAndCompletionIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private IShipmentService shipmentService;
    @Autowired
    private ITradeCompletionService tradeCompletionService;

    private TestMember seller;
    private TestMember buyer;
    private long orderId;
    private Instant deliveredAt;

    private void shippedOrder(long price, long quantity) throws Exception {
        seller = member("seller");
        buyer = member("buyer");
        long productId = registerProduct(seller, price, quantity);
        charge(buyer, price * quantity);
        orderId = placeOrder(buyer, productId, quantity, null).get("orderId").asLong();
        ship(seller, orderId);
    }

    private void deliveredOrder(long price, long quantity) throws Exception {
        shippedOrder(price, quantity);
        deliveredAt = clock.instant().plus(Duration.ofMinutes(2));
        clock.setInstant(deliveredAt);
        assertThat(shipmentService.completeDueDeliveries(deliveredAt)).isEqualTo(1);
    }

    /* 컬럼에는 UTC 벽시계 값이 저장된다(J-09). JDBC 기본 조회는 JVM 시간대로 해석하므로 UTC 로 다시 붙인다. */
    private Instant column(String name) {
        Timestamp value = jdbcTemplate.queryForObject(
                "SELECT " + name + " FROM PURCHASE_ORDER WHERE ORDER_ID = ?", Timestamp.class, orderId);
        return value == null ? null : value.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    @Test
    void F014_AC01_배송_중_주문은_완료_예정_시각에_배송_완료가_되고_48시간_확인_기간이_시작된다() throws Exception {
        shippedOrder(30_000, 1);
        Instant dueAt = clock.instant().plus(Duration.ofMinutes(2));

        assertThat(shipmentService.completeDueDeliveries(dueAt.minusNanos(1000))).isZero();
        assertThat(shippingStatus(orderId)).isEqualTo("SHIPPING");

        assertThat(shipmentService.completeDueDeliveries(dueAt)).isEqualTo(1);
        assertThat(shippingStatus(orderId)).isEqualTo("DELIVERED");
        assertThat(tradeStatus(orderId)).isEqualTo("IN_PROGRESS");
        assertThat(column("DELIVERED_AT")).isEqualTo(dueAt);
        assertThat(column("INSPECTION_DEADLINE_AT")).isEqualTo(dueAt.plus(Duration.ofHours(48)));
        // 배송 완료 자체는 정상 완료가 아니며 판매대금을 지급하지 않는다.
        assertThat(balanceOf(seller)).isZero();
    }

    @Test
    void F014_AC02_같은_주문에_다시_실행해도_배송_완료_시각과_확인_기한이_바뀌지_않는다() throws Exception {
        deliveredOrder(30_000, 1);
        Instant firstDelivered = column("DELIVERED_AT");
        Instant firstDeadline = column("INSPECTION_DEADLINE_AT");

        assertThat(shipmentService.completeDueDeliveries(deliveredAt.plus(Duration.ofHours(5)))).isZero();
        assertThat(column("DELIVERED_AT")).isEqualTo(firstDelivered);
        assertThat(column("INSPECTION_DEADLINE_AT")).isEqualTo(firstDeadline);
    }

    @Test
    void F014_발송_전_주문과_취소된_주문은_배송_완료로_바꾸지_않는다() throws Exception {
        seller = member("seller");
        buyer = member("buyer");
        long productId = registerProduct(seller, 10_000, 2);
        charge(buyer, 20_000);
        long waiting = placeOrder(buyer, productId, 1, null).get("orderId").asLong();
        long cancelled = placeOrder(buyer, productId, 1, null).get("orderId").asLong();
        cancel(buyer, cancelled);

        assertThat(shipmentService.completeDueDeliveries(clock.instant().plus(Duration.ofDays(3)))).isZero();
        assertThat(shippingStatus(waiting)).isEqualTo("WAITING_SHIPMENT");
        assertThat(tradeStatus(cancelled)).isEqualTo("CANCELLED");
        assertThat(count("SELECT COUNT(*) FROM PURCHASE_ORDER WHERE ORDER_ID IN (?, ?) AND DELIVERED_AT IS NOT NULL",
                waiting, cancelled)).isZero();
    }

    @Test
    void F015_AC01_배송_완료_47시간_뒤_구매자가_확인하면_정상_완료되고_판매대금이_한_번_지급된다() throws Exception {
        deliveredOrder(30_000, 1);
        clock.setInstant(deliveredAt.plus(Duration.ofHours(47)));

        JsonNode completed = json(postJson("/api/orders/" + orderId + "/confirm-receipt", buyer, null)
                .andExpect(status().isOk()));
        assertThat(completed.get("tradeStatus").asText()).isEqualTo("COMPLETED");
        assertThat(completed.get("completionCause").asText()).isEqualTo("BUYER_CONFIRMED");
        assertThat(completed.get("finalizedAt").isNull()).isFalse();
        assertThat(balanceOf(seller)).isEqualTo(30_000);
        assertThat(transactionCount(seller, "SALE_PAYOUT")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM BALANCE_TRANSACTION WHERE MEMBER_ID = ? AND TYPE = 'SALE_PAYOUT' AND ORDER_ID = ?",
                seller.id(), orderId)).isEqualTo(1);
        // 구매자에게는 반환이 없다(BR-005).
        assertThat(balanceOf(buyer)).isZero();
    }

    @Test
    void F015_AC03_정확히_48시간에는_수동_확인이_기한_종료로_거절된다() throws Exception {
        deliveredOrder(30_000, 1);
        clock.setInstant(deliveredAt.plus(Duration.ofHours(48)));
        JsonNode error = json(postJson("/api/orders/" + orderId + "/confirm-receipt", buyer, null)
                .andExpect(status().isConflict()));
        assertThat(error.get("code").asText()).isEqualTo("INSPECTION_PERIOD_ENDED");
        assertThat(tradeStatus(orderId)).isEqualTo("IN_PROGRESS");
        assertThat(balanceOf(seller)).isZero();
    }

    @Test
    void F015_완료한_뒤_다시_확인하면_409이고_지급은_한_번이다() throws Exception {
        deliveredOrder(30_000, 1);
        postJson("/api/orders/" + orderId + "/confirm-receipt", buyer, null).andExpect(status().isOk());
        JsonNode again = json(postJson("/api/orders/" + orderId + "/confirm-receipt", buyer, null)
                .andExpect(status().isConflict()));
        assertThat(again.get("code").asText()).isEqualTo("TRADE_ALREADY_FINALIZED");
        assertThat(transactionCount(seller, "SALE_PAYOUT")).isEqualTo(1);
        assertThat(balanceOf(seller)).isEqualTo(30_000);
        // 정상 완료 뒤에는 자동 완료 대상도 아니다.
        assertThat(tradeCompletionService.completeExpiredInspections(deliveredAt.plus(Duration.ofDays(3)))).isZero();
        assertThat(transactionCount(seller, "SALE_PAYOUT")).isEqualTo(1);
    }

    @Test
    void F015_배송_완료_전이면_409_판매자가_확인하면_403이다() throws Exception {
        shippedOrder(30_000, 1);
        JsonNode notDelivered = json(postJson("/api/orders/" + orderId + "/confirm-receipt", buyer, null)
                .andExpect(status().isConflict()));
        assertThat(notDelivered.get("code").asText()).isEqualTo("ORDER_NOT_DELIVERED");
        JsonNode bySeller = json(postJson("/api/orders/" + orderId + "/confirm-receipt", seller, null)
                .andExpect(status().isForbidden()));
        assertThat(bySeller.get("code").asText()).isEqualTo("NOT_BUYER");
        assertThat(balanceOf(seller)).isZero();
    }

    @Test
    void F016_AC01_환불_요청_없이_정확히_48시간이_되면_자동_완료되고_대금은_한_번이다() throws Exception {
        deliveredOrder(30_000, 1);
        Instant deadline = deliveredAt.plus(Duration.ofHours(48));

        assertThat(tradeCompletionService.completeExpiredInspections(deadline.minusSeconds(1))).isZero();
        assertThat(tradeStatus(orderId)).isEqualTo("IN_PROGRESS");

        assertThat(tradeCompletionService.completeExpiredInspections(deadline)).isEqualTo(1);
        assertThat(tradeStatus(orderId)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT COMPLETION_CAUSE FROM PURCHASE_ORDER WHERE ORDER_ID = ?",
                String.class, orderId)).isEqualTo("AUTO_EXPIRED");
        assertThat(balanceOf(seller)).isEqualTo(30_000);

        assertThat(tradeCompletionService.completeExpiredInspections(deadline.plus(Duration.ofHours(1)))).isZero();
        assertThat(transactionCount(seller, "SALE_PAYOUT")).isEqualTo(1);
    }

    @Test
    void 복수_수량_주문의_판매대금은_그_주문의_총_결제액_전액이다() throws Exception {
        deliveredOrder(10_000, 3);
        postJson("/api/orders/" + orderId + "/confirm-receipt", buyer, null).andExpect(status().isOk());
        assertThat(balanceOf(seller)).isEqualTo(30_000);
    }

    @Test
    void BR003_지급_후_판매자_잔액이_저장_범위를_넘으면_수동_확인은_400이고_자동_완료는_그_건을_바꾸지_않는다() throws Exception {
        deliveredOrder(10, 1);
        charge(seller, 999_999_999_999_995L);

        JsonNode manual = json(postJson("/api/orders/" + orderId + "/confirm-receipt", buyer, null)
                .andExpect(status().isBadRequest()));
        assertThat(manual.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(tradeCompletionService.completeExpiredInspections(deliveredAt.plus(Duration.ofHours(48)))).isZero();
        assertThat(tradeStatus(orderId)).isEqualTo("IN_PROGRESS");
        assertThat(transactionCount(seller, "SALE_PAYOUT")).isZero();
        assertThat(balanceOf(seller)).isEqualTo(999_999_999_999_995L);
    }
}
