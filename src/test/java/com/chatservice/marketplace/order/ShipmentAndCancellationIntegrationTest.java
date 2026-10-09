package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.order.service.IOrderCancellationService;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * F-011 발송 정보 등록, F-012 발송 전 취소, F-013 미발송 자동 취소, F-004-AC-01(설계 9장).
 * 자동 처리는 스케줄러를 기다리지 않고 서비스 메서드를 직접 호출한다.
 * 발송과 취소의 동시 요청, 자동 취소와 수동 취소의 겹침은 후속 과제이며 여기서는 순차 요청만 본다.
 */
class ShipmentAndCancellationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private IOrderCancellationService cancellationService;

    private TestMember seller;
    private TestMember buyer;

    private static Instant kst(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(TimeRules.BUSINESS_ZONE).toInstant();
    }

    /** 2026-10-05(월) 10:00 KST 에 확정된 주문 하나를 만든다. 발송 기한은 2026-10-13(화) 00:00 KST. */
    private long mondayOrder(long price, long stock, long quantity) throws Exception {
        seller = member("seller");
        buyer = member("buyer");
        long productId = registerProduct(seller, price, stock);
        charge(buyer, price * quantity);
        return placeOrder(buyer, productId, quantity, null).get("orderId").asLong();
    }

    @Test
    void F011_AC01_기한_안의_발송_대기_주문에_처음_등록하면_배송_중이고_모의_배송이_시작된다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);
        clock.advance(Duration.ofHours(3));

        JsonNode shipped = ship(seller, orderId);
        assertThat(shipped.get("shippingStatus").asText()).isEqualTo("SHIPPING");
        assertThat(shipped.get("tradeStatus").asText()).isEqualTo("IN_PROGRESS");
        assertThat(shipped.get("myRole").asText()).isEqualTo("SELLER");
        Instant shippedAt = Instant.parse(shipped.get("shipment").get("shippedAt").asText());
        Instant dueAt = Instant.parse(shipped.get("shipment").get("deliveryDueAt").asText());
        assertThat(shippedAt).isEqualTo(clock.instant());
        // marketplace.mock-delivery.duration = PT2M
        assertThat(Duration.between(shippedAt, dueAt)).isEqualTo(Duration.ofMinutes(2));
        assertThat(shipped.get("shipment").get("carrierName").asText()).isEqualTo("테스트택배");
        // 발송만으로 판매자 잔액이 늘지 않는다.
        assertThat(balanceOf(seller)).isZero();
    }

    @Test
    void F011_AC02_월요일_확정_주문은_다음_화요일_0시_KST부터_발송할_수_없다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);
        clock.setInstant(kst("2026-10-13T00:00:00"));

        JsonNode error = json(postJson("/api/orders/" + orderId + "/shipment", seller,
                Map.of("carrierName", "테스트택배", "trackingNumber", "1")).andExpect(status().isConflict()));
        assertThat(error.get("code").asText()).isEqualTo("SHIPMENT_DEADLINE_PASSED");
        assertThat(shippingStatus(orderId)).isEqualTo("WAITING_SHIPMENT");
        assertThat(count("SELECT COUNT(*) FROM SHIPMENT WHERE ORDER_ID = ?", orderId)).isZero();
    }

    @Test
    void F011_발송_기한_직전인_월요일_23시59분59초_KST에는_발송할_수_있다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);
        clock.setInstant(kst("2026-10-12T23:59:59.999999"));
        ship(seller, orderId);
        assertThat(shippingStatus(orderId)).isEqualTo("SHIPPING");
    }

    @Test
    void F011_AC03_등록한_뒤_다른_운송장을_입력하면_409이고_원래_정보가_유지된다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);
        ship(seller, orderId);
        JsonNode error = json(postJson("/api/orders/" + orderId + "/shipment", seller,
                Map.of("carrierName", "다른택배", "trackingNumber", "9999")).andExpect(status().isConflict()));
        assertThat(error.get("code").asText()).isEqualTo("ALREADY_SHIPPED");
        assertThat(jdbcTemplate.queryForObject("SELECT TRACKING_NUMBER FROM SHIPMENT WHERE ORDER_ID = ?", String.class, orderId))
                .isEqualTo("1234-5678");
        assertThat(count("SELECT COUNT(*) FROM SHIPMENT WHERE ORDER_ID = ?", orderId)).isEqualTo(1);
    }

    @Test
    void F011_구매자는_403_취소된_주문은_409_빈_입력은_400_없는_주문은_404이다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);
        JsonNode byBuyer = json(postJson("/api/orders/" + orderId + "/shipment", buyer,
                Map.of("carrierName", "택배", "trackingNumber", "1")).andExpect(status().isForbidden()));
        assertThat(byBuyer.get("code").asText()).isEqualTo("NOT_SELLER");
        JsonNode blank = json(postJson("/api/orders/" + orderId + "/shipment", seller,
                Map.of("carrierName", " ", "trackingNumber", "1")).andExpect(status().isBadRequest()));
        assertThat(blank.get("fieldErrors").has("carrierName")).isTrue();
        postJson("/api/orders/999999999/shipment", seller, Map.of("carrierName", "택배", "trackingNumber", "1"))
                .andExpect(status().isNotFound());

        cancel(buyer, orderId);
        JsonNode cancelled = json(postJson("/api/orders/" + orderId + "/shipment", seller,
                Map.of("carrierName", "택배", "trackingNumber", "1")).andExpect(status().isConflict()));
        assertThat(cancelled.get("code").asText()).isEqualTo("ORDER_NOT_IN_PROGRESS");
        assertThat(count("SELECT COUNT(*) FROM SHIPMENT WHERE ORDER_ID = ?", orderId)).isZero();
    }

    @Test
    void F012_AC01_발송_대기_주문을_구매자가_취소하면_전액_반환되고_재고와_상품_상태는_그대로다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);
        long productId = jdbcTemplate.queryForObject("SELECT PRODUCT_ID FROM PURCHASE_ORDER WHERE ORDER_ID = ?", Long.class, orderId);
        assertThat(balanceOf(buyer)).isZero();

        JsonNode cancelled = json(postJson("/api/orders/" + orderId + "/cancel", buyer, Map.of("reason", "주소 변경"))
                .andExpect(status().isOk()));
        assertThat(cancelled.get("tradeStatus").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("shippingStatus").asText()).isEqualTo("WAITING_SHIPMENT");
        assertThat(cancelled.get("cancellation").get("cancelledBy").asText()).isEqualTo("BUYER");
        assertThat(cancelled.get("cancellation").get("reason").asText()).isEqualTo("주소 변경");
        assertThat(cancelled.get("finalizedAt").isNull()).isFalse();
        JsonNode transactions = cancelled.get("myBalanceTransactions");
        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(1).get("type").asText()).isEqualTo("CANCEL_REFUND");
        assertThat(transactions.get(1).get("amount").asLong()).isEqualTo(30_000);

        assertThat(balanceOf(buyer)).isEqualTo(30_000);
        assertThat(balanceOf(seller)).isZero();
        // 차감한 재고는 복구하지 않고 판매 종료 상태도 유지한다(BR-002).
        assertThat(remainingQuantity(productId)).isZero();
        assertThat(productStatus(productId)).isEqualTo("SOLD");
    }

    @Test
    void F012_판매자가_취소해도_구매자에게_반환되고_취소_주체는_SELLER다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);
        JsonNode cancelled = cancel(seller, orderId);
        assertThat(cancelled.get("cancellation").get("cancelledBy").asText()).isEqualTo("SELLER");
        assertThat(cancelled.get("myBalanceTransactions")).isEmpty();
        assertThat(balanceOf(buyer)).isEqualTo(30_000);
        assertThat(balanceOf(seller)).isZero();
    }

    @Test
    void F012_AC02_배송_중_주문은_취소할_수_없다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);
        ship(seller, orderId);
        JsonNode error = json(postJson("/api/orders/" + orderId + "/cancel", buyer, Map.of("reason", "변심"))
                .andExpect(status().isConflict()));
        assertThat(error.get("code").asText()).isEqualTo("ORDER_ALREADY_SHIPPED");
        assertThat(tradeStatus(orderId)).isEqualTo("IN_PROGRESS");
        assertThat(balanceOf(buyer)).isZero();
    }

    @Test
    void F012_AC03_취소한_뒤_다시_취소하면_409이고_반환은_한_번이다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);
        cancel(buyer, orderId);
        JsonNode again = json(postJson("/api/orders/" + orderId + "/cancel", seller, Map.of("reason", "다시"))
                .andExpect(status().isConflict()));
        assertThat(again.get("code").asText()).isEqualTo("TRADE_ALREADY_FINALIZED");
        assertThat(transactionCount(buyer, "CANCEL_REFUND")).isEqualTo(1);
        assertThat(balanceOf(buyer)).isEqualTo(30_000);
    }

    @Test
    void F012_당사자가_아니면_403_사유가_없으면_400이고_주문과_잔액이_그대로다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);
        JsonNode outsider = json(postJson("/api/orders/" + orderId + "/cancel", member("outsider"), Map.of("reason", "x"))
                .andExpect(status().isForbidden()));
        assertThat(outsider.get("code").asText()).isEqualTo("NOT_TRADE_PARTY");
        postJson("/api/orders/" + orderId + "/cancel", buyer, Map.of("reason", " ")).andExpect(status().isBadRequest());
        postJson("/api/orders/" + orderId + "/cancel", buyer, "{}").andExpect(status().isBadRequest());
        assertThat(tradeStatus(orderId)).isEqualTo("IN_PROGRESS");
        assertThat(balanceOf(buyer)).isZero();
    }

    @Test
    void F013_AC01_월요일_확정_미발송_주문은_화요일_0시_KST에_자동_취소되고_전액_반환된다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);

        assertThat(cancellationService.cancelExpiredUnshipped(kst("2026-10-12T23:59:59.999999"))).isZero();
        assertThat(tradeStatus(orderId)).isEqualTo("IN_PROGRESS");

        Instant deadline = kst("2026-10-13T00:00:00");
        assertThat(cancellationService.cancelExpiredUnshipped(deadline)).isEqualTo(1);
        assertThat(tradeStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT CANCELLED_BY FROM PURCHASE_ORDER WHERE ORDER_ID = ?", String.class, orderId))
                .isEqualTo("SYSTEM");
        assertThat(jdbcTemplate.queryForObject("SELECT CANCEL_REASON FROM PURCHASE_ORDER WHERE ORDER_ID = ?", String.class, orderId))
                .isEqualTo("SHIPMENT_DEADLINE_EXPIRED");
        assertThat(balanceOf(buyer)).isEqualTo(30_000);
        assertThat(transactionCount(buyer, "CANCEL_REFUND")).isEqualTo(1);

        // 반복 실행해도 이미 끝난 주문은 대상에서 빠지므로 반환은 한 번이다.
        assertThat(cancellationService.cancelExpiredUnshipped(deadline.plus(Duration.ofDays(1)))).isZero();
        assertThat(transactionCount(buyer, "CANCEL_REFUND")).isEqualTo(1);
    }

    @Test
    void F013_AC02_이미_배송_중인_주문은_자동_취소하지_않는다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);
        ship(seller, orderId);
        assertThat(cancellationService.cancelExpiredUnshipped(kst("2026-10-20T00:00:00"))).isZero();
        assertThat(tradeStatus(orderId)).isEqualTo("IN_PROGRESS");
        assertThat(shippingStatus(orderId)).isEqualTo("SHIPPING");
        assertThat(balanceOf(buyer)).isZero();
    }

    @Test
    void BR002_복수_수량_주문을_취소하면_그_주문의_결제액만_반환되고_재고와_다른_주문은_바뀌지_않는다() throws Exception {
        seller = member("seller");
        buyer = member("buyer");
        TestMember other = member("other");
        long productId = registerProduct(seller, 10_000, 5);
        charge(buyer, 30_000);
        charge(other, 20_000);
        long cancelled = placeOrder(buyer, productId, 3, null).get("orderId").asLong();
        long kept = placeOrder(other, productId, 2, null).get("orderId").asLong();
        assertThat(productStatus(productId)).isEqualTo("SOLD");

        cancel(buyer, cancelled);
        assertThat(balanceOf(buyer)).isEqualTo(30_000);
        assertThat(remainingQuantity(productId)).isZero();
        assertThat(productStatus(productId)).isEqualTo("SOLD");
        assertThat(tradeStatus(kept)).isEqualTo("IN_PROGRESS");
        assertThat(balanceOf(other)).isZero();

        // 재고가 남은 상품의 주문을 자동 취소해도 남은 수량은 그대로다.
        long secondProduct = registerProduct(seller, 10_000, 4);
        charge(buyer, 20_000);
        long autoCancelled = placeOrder(buyer, secondProduct, 2, null).get("orderId").asLong();
        cancellationService.cancelExpiredUnshipped(kst("2026-10-13T00:00:00"));
        assertThat(tradeStatus(autoCancelled)).isEqualTo("CANCELLED");
        assertThat(remainingQuantity(secondProduct)).isEqualTo(2);
        assertThat(productStatus(secondProduct)).isEqualTo("ON_SALE");
        assertThat(balanceOf(buyer)).isEqualTo(50_000);
    }

    @Test
    void BR003_반환_후_잔액이_저장_범위를_넘으면_수동_취소는_400이고_자동_취소는_그_건을_바꾸지_않는다() throws Exception {
        long orderId = mondayOrder(10, 1, 1);
        charge(buyer, 999_999_999_999_995L);

        JsonNode manual = json(postJson("/api/orders/" + orderId + "/cancel", buyer, Map.of("reason", "변심"))
                .andExpect(status().isBadRequest()));
        assertThat(manual.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(tradeStatus(orderId)).isEqualTo("IN_PROGRESS");

        assertThat(cancellationService.cancelExpiredUnshipped(kst("2026-10-13T00:00:00"))).isZero();
        assertThat(tradeStatus(orderId)).isEqualTo("IN_PROGRESS");
        assertThat(balanceOf(buyer)).isEqualTo(999_999_999_999_995L);
        assertThat(transactionCount(buyer, "CANCEL_REFUND")).isZero();
    }

    @Test
    void F004_AC01_충전_구매_반환_내역을_유형_금액_변동후잔액_시각_주문_연결과_함께_조회한다() throws Exception {
        long orderId = mondayOrder(30_000, 1, 1);
        Instant cancelledAt = MONDAY_10_KST.plus(Duration.ofHours(1));
        clock.setInstant(cancelledAt);
        cancel(buyer, orderId);

        JsonNode transactions = json(getJson("/api/wallet/transactions", buyer).andExpect(status().isOk()));
        assertThat(transactions).hasSize(3);
        JsonNode refund = transactions.get(0);
        JsonNode purchase = transactions.get(1);
        JsonNode chargeTx = transactions.get(2);

        assertThat(refund.get("type").asText()).isEqualTo("CANCEL_REFUND");
        assertThat(refund.get("amount").asLong()).isEqualTo(30_000);
        assertThat(refund.get("balanceAfter").asLong()).isEqualTo(30_000);
        assertThat(refund.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(Instant.parse(refund.get("createdAt").asText())).isEqualTo(cancelledAt);

        assertThat(purchase.get("type").asText()).isEqualTo("PURCHASE");
        assertThat(purchase.get("amount").asLong()).isEqualTo(-30_000);
        assertThat(purchase.get("balanceAfter").asLong()).isZero();
        assertThat(purchase.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(Instant.parse(purchase.get("createdAt").asText())).isEqualTo(MONDAY_10_KST);

        assertThat(chargeTx.get("type").asText()).isEqualTo("CHARGE");
        assertThat(chargeTx.get("orderId").isNull()).isTrue();
        assertThat(json(getJson("/api/wallet", buyer)).get("balance").asLong()).isEqualTo(30_000);
    }
}
