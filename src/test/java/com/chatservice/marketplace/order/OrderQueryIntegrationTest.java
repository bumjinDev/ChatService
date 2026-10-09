package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.order.service.IShipmentService;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.fasterxml.jackson.databind.JsonNode;

/** F-019 주문·배송·결제·최종 금전 결과 조회와 NFR-003 주문 개인정보 접근 통제(설계 9장 F-019-AC-01~03). */
class OrderQueryIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private IShipmentService shipmentService;

    @Test
    void F019_AC01_결제_성공_응답을_받지_못해도_구매_목록과_상세에서_주문과_차감_결과를_확인한다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 10_000, 5);
        charge(buyer, 50_000);
        long orderId = placeOrder(buyer, productId, 2, null).get("orderId").asLong();

        JsonNode purchases = json(getJson("/api/orders/purchases", buyer).andExpect(status().isOk()));
        assertThat(purchases).hasSize(1);
        JsonNode summary = purchases.get(0);
        assertThat(summary.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(summary.get("product").get("productId").asLong()).isEqualTo(productId);
        assertThat(summary.get("quantity").asLong()).isEqualTo(2);
        assertThat(summary.get("paidAmount").asLong()).isEqualTo(20_000);
        assertThat(summary.get("priceSource").asText()).isEqualTo("LISTED");
        assertThat(summary.has("recipientName")).isFalse();

        JsonNode detail = json(getJson("/api/orders/" + orderId, buyer).andExpect(status().isOk()));
        assertThat(detail.get("paidAmount").asLong()).isEqualTo(20_000);
        assertThat(detail.get("product").get("description").asText()).isEqualTo("상품 설명");
        assertThat(detail.get("myBalanceTransactions").get(0).get("type").asText()).isEqualTo("PURCHASE");
        assertThat(detail.get("myBalanceTransactions").get(0).get("balanceAfter").asLong()).isEqualTo(30_000);
        assertThat(detail.get("shipment").isNull()).isTrue();
        assertThat(detail.get("cancellation").isNull()).isTrue();
        assertThat(detail.get("refundRequest").isNull()).isTrue();

        // 반복 조회는 상태를 바꾸지 않는다.
        getJson("/api/orders/" + orderId, buyer).andExpect(status().isOk());
        assertThat(balanceOf(buyer)).isEqualTo(30_000);
        assertThat(transactionCount(buyer, "PURCHASE")).isEqualTo(1);
    }

    @Test
    void F019_AC02_환불_완료_뒤_당사자는_최종_상태와_본인의_반환_결과를_확인한다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 30_000, 1);
        charge(buyer, 30_000);
        long orderId = placeOrder(buyer, productId, 1, null).get("orderId").asLong();
        ship(seller, orderId);
        clock.advance(Duration.ofMinutes(2));
        shipmentService.completeDueDeliveries(clock.instant());
        postJson("/api/orders/" + orderId + "/refund-request", buyer,
                Map.of("reasonCode", "DESCRIPTION_MISMATCH", "detail", "설명과 다름")).andExpect(status().isCreated());
        postJson("/api/orders/" + orderId + "/refund-request/approve", seller, null).andExpect(status().isOk());

        JsonNode buyerView = json(getJson("/api/orders/" + orderId, buyer).andExpect(status().isOk()));
        assertThat(buyerView.get("tradeStatus").asText()).isEqualTo("REFUNDED");
        assertThat(buyerView.get("refundRequest").get("decision").asText()).isEqualTo("APPROVED");
        assertThat(buyerView.get("shipment").get("trackingNumber").asText()).isEqualTo("1234-5678");
        assertThat(buyerView.get("deliveredAt").isNull()).isFalse();
        JsonNode buyerTx = buyerView.get("myBalanceTransactions");
        assertThat(buyerTx).hasSize(2);
        assertThat(buyerTx.get(1).get("type").asText()).isEqualTo("REFUND");
        assertThat(buyerTx.get(1).get("amount").asLong()).isEqualTo(30_000);

        JsonNode sellerView = json(getJson("/api/orders/" + orderId, seller).andExpect(status().isOk()));
        assertThat(sellerView.get("tradeStatus").asText()).isEqualTo("REFUNDED");
        assertThat(sellerView.get("myRole").asText()).isEqualTo("SELLER");
        assertThat(sellerView.get("myBalanceTransactions")).isEmpty();

        JsonNode sales = json(getJson("/api/orders/sales", seller));
        assertThat(sales.get(0).get("tradeStatus").asText()).isEqualTo("REFUNDED");
        assertThat(sales.get(0).get("finalizedAt").isNull()).isFalse();
    }

    @Test
    void F019_AC03_제3자는_주문_상세를_받을_수_없고_수령인과_주소가_노출되지_않는다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 10_000, 1);
        // 기존 문의자도 주문 당사자 권한을 얻지 않는다(BR-008).
        TestMember inquirer = member("inquirer");
        startConversation(inquirer, productId);
        charge(buyer, 10_000);
        long orderId = placeOrder(buyer, productId, 1, null).get("orderId").asLong();

        for (TestMember outsider : new TestMember[] {member("outsider"), inquirer}) {
            String body = getJson("/api/orders/" + orderId, outsider).andExpect(status().isForbidden())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            JsonNode error = objectMapper.readTree(body);
            assertThat(error.get("code").asText()).isEqualTo("NOT_TRADE_PARTY");
            assertThat(body).doesNotContain("홍길동").doesNotContain("세종대로");
        }
        getJson("/api/orders/999999999", buyer).andExpect(status().isNotFound());
        getJson("/api/orders/" + orderId, null).andExpect(status().isUnauthorized());
    }

    @Test
    void 구매_목록과_판매_목록은_본인이_당사자인_주문만_최신순으로_돌려준다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyerA = member("buyerA");
        TestMember buyerB = member("buyerB");
        long productId = registerProduct(seller, 10_000, 5);
        charge(buyerA, 10_000);
        charge(buyerB, 20_000);
        long orderA = placeOrder(buyerA, productId, 1, null).get("orderId").asLong();
        clock.advance(Duration.ofMinutes(1));
        long orderB = placeOrder(buyerB, productId, 2, null).get("orderId").asLong();

        JsonNode purchasesA = json(getJson("/api/orders/purchases", buyerA));
        assertThat(purchasesA).hasSize(1);
        assertThat(purchasesA.get(0).get("orderId").asLong()).isEqualTo(orderA);

        JsonNode sales = json(getJson("/api/orders/sales", seller));
        assertThat(sales).hasSize(2);
        assertThat(sales.get(0).get("orderId").asLong()).isEqualTo(orderB);
        assertThat(sales.get(1).get("orderId").asLong()).isEqualTo(orderA);

        assertThat(json(getJson("/api/orders/sales", buyerA))).isEmpty();
        assertThat(json(getJson("/api/orders/purchases", seller))).isEmpty();
    }
}
