package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * F-010 구매·잔액 결제와 합의 가격 적용(F-009-AC-01·02), 복수 수량 결제(F-010-AC-03·05~08), 금액 범위(BR-003).
 * F-010-AC-01·02·04(동시 결제, 같은 결제 재시도)는 후속 과제이며 여기서 검증하지 않는다.
 */
class OrderPaymentIntegrationTest extends IntegrationTestSupport {

    private static Instant kst(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(TimeRules.BUSINESS_ZONE).toInstant();
    }

    private void assertUnchanged(TestMember buyer, long balance, long productId, long remaining, long orders) {
        assertThat(balanceOf(buyer)).isEqualTo(balance);
        assertThat(transactionCount(buyer, "PURCHASE")).isZero();
        assertThat(remainingQuantity(productId)).isEqualTo(remaining);
        assertThat(orderCount(productId)).isEqualTo(orders);
    }

    @Test
    void F010_정상_결제는_잔액_차감_PURCHASE_내역_재고_차감_주문_생성과_당사자_대화를_함께_반영한다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 30_000, 1);
        charge(buyer, 50_000);

        JsonNode order = placeOrder(buyer, productId, 1, null);
        long orderId = order.get("orderId").asLong();

        assertThat(order.get("tradeStatus").asText()).isEqualTo("IN_PROGRESS");
        assertThat(order.get("shippingStatus").asText()).isEqualTo("WAITING_SHIPMENT");
        assertThat(order.get("quantity").asLong()).isEqualTo(1);
        assertThat(order.get("unitPrice").asLong()).isEqualTo(30_000);
        assertThat(order.get("paidAmount").asLong()).isEqualTo(30_000);
        assertThat(order.get("priceSource").asText()).isEqualTo("LISTED");
        assertThat(order.get("myRole").asText()).isEqualTo("BUYER");
        assertThat(order.get("recipientName").asText()).isEqualTo("홍길동");
        // 2026-10-05(월) 10:00 KST 확정 → 다음 주 화요일 00:00 KST 에 발송 기한이 지난다.
        assertThat(Instant.parse(order.get("shipDeadlineAt").asText())).isEqualTo(kst("2026-10-13T00:00:00"));
        assertThat(order.get("myBalanceTransactions")).hasSize(1);
        assertThat(order.get("myBalanceTransactions").get(0).get("type").asText()).isEqualTo("PURCHASE");
        assertThat(order.get("myBalanceTransactions").get(0).get("amount").asLong()).isEqualTo(-30_000);

        assertThat(balanceOf(buyer)).isEqualTo(20_000);
        assertThat(count("SELECT COUNT(*) FROM BALANCE_TRANSACTION WHERE MEMBER_ID = ? AND TYPE = 'PURCHASE'"
                + " AND AMOUNT = -30000 AND BALANCE_AFTER = 20000 AND ORDER_ID = ?", buyer.id(), orderId)).isEqualTo(1);
        assertThat(remainingQuantity(productId)).isZero();
        assertThat(productStatus(productId)).isEqualTo("SOLD");
        assertThat(count("SELECT COUNT(*) FROM PRODUCT WHERE PRODUCT_ID = ? AND SOLD_AT IS NOT NULL", productId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM CONVERSATION WHERE PRODUCT_ID = ? AND BUYER_ID = ? AND CREATED_BY_SYSTEM = 1",
                productId, buyer.id())).isEqualTo(1);
        // 구매·발송만으로 판매자 잔액이 늘지 않는다(BR-003).
        assertThat(balanceOf(seller)).isZero();
    }

    @Test
    void F010_AC03_합의_가격이_있어도_선택하지_않으면_등록_단가로_계산한다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 50_000, 5);
        long conversationId = startConversation(buyer, productId);
        respond(seller, propose(buyer, conversationId, 40_000), true);
        charge(buyer, 100_000);

        JsonNode order = placeOrder(buyer, productId, 2, null);
        assertThat(order.get("unitPrice").asLong()).isEqualTo(50_000);
        assertThat(order.get("paidAmount").asLong()).isEqualTo(100_000);
        assertThat(order.get("priceSource").asText()).isEqualTo("LISTED");
        assertThat(order.get("offerId").isNull()).isTrue();
        assertThat(balanceOf(buyer)).isZero();
    }

    @Test
    void F009_AC01_수락_제안이_있는_구매자가_재접속해_합의_가격으로_결제한다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 50_000, 1);
        long conversationId = startConversation(buyer, productId);
        long offerId = propose(buyer, conversationId, 40_000);
        respond(seller, offerId, true);
        charge(buyer, 40_000);

        JsonNode detail = json(getJson("/api/conversations/" + conversationId, buyer));
        assertThat(detail.get("offers").get(0).get("status").asText()).isEqualTo("ACCEPTED");

        JsonNode order = placeOrder(buyer, productId, 1, offerId);
        assertThat(order.get("priceSource").asText()).isEqualTo("AGREED");
        assertThat(order.get("offerId").asLong()).isEqualTo(offerId);
        assertThat(order.get("unitPrice").asLong()).isEqualTo(40_000);
        assertThat(order.get("paidAmount").asLong()).isEqualTo(40_000);
        assertThat(balanceOf(buyer)).isZero();
    }

    @Test
    void F010_잔액_부족_판매_종료_본인_상품_잘못된_제안_수령인_누락은_거절되고_아무것도_바뀌지_않는다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 30_000, 2);
        charge(buyer, 20_000);

        JsonNode insufficient = json(postJson("/api/orders", buyer, orderBody(productId, 1, null)).andExpect(status().isConflict()));
        assertThat(insufficient.get("code").asText()).isEqualTo("INSUFFICIENT_BALANCE");
        assertUnchanged(buyer, 20_000, productId, 2, 0);

        JsonNode self = json(postJson("/api/orders", seller, orderBody(productId, 1, null)).andExpect(status().isForbidden()));
        assertThat(self.get("code").asText()).isEqualTo("SELF_TRADE_NOT_ALLOWED");

        Map<String, Object> noRecipient = orderBody(productId, 1, null);
        noRecipient.remove("recipientName");
        JsonNode missing = json(postJson("/api/orders", buyer, noRecipient).andExpect(status().isBadRequest()));
        assertThat(missing.get("fieldErrors").has("recipientName")).isTrue();
        Map<String, Object> blankAddress = orderBody(productId, 1, null);
        blankAddress.put("shippingAddress", " ");
        postJson("/api/orders", buyer, blankAddress).andExpect(status().isBadRequest());

        postJson("/api/orders", buyer, orderBody(999_999_999L, 1, null)).andExpect(status().isNotFound());
        assertUnchanged(buyer, 20_000, productId, 2, 0);

        // 판매 종료 상품
        long soldProduct = registerProduct(seller, 10_000, 1);
        TestMember other = member("other");
        charge(other, 10_000);
        placeOrder(other, soldProduct, 1, null);
        JsonNode sold = json(postJson("/api/orders", buyer, orderBody(soldProduct, 1, null)).andExpect(status().isConflict()));
        assertThat(sold.get("code").asText()).isEqualTo("PRODUCT_NOT_ON_SALE");
        assertThat(balanceOf(buyer)).isEqualTo(20_000);
        assertThat(orderCount(soldProduct)).isEqualTo(1);
    }

    @Test
    void F010_없거나_수락되지_않았거나_다른_상품_다른_구매자의_제안은_INVALID_OFFER_SELECTION이다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        TestMember otherBuyer = member("otherBuyer");
        long productId = registerProduct(seller, 50_000, 3);
        long otherProductId = registerProduct(seller, 50_000, 3);
        charge(buyer, 200_000);

        long pendingOffer = propose(buyer, startConversation(buyer, productId), 40_000);
        long otherProductOffer = propose(buyer, startConversation(buyer, otherProductId), 30_000);
        respond(seller, otherProductOffer, true);
        long otherBuyerOffer = propose(otherBuyer, startConversation(otherBuyer, productId), 20_000);
        respond(seller, otherBuyerOffer, true);

        for (long offerId : new long[] {999_999_999L, pendingOffer, otherProductOffer, otherBuyerOffer}) {
            JsonNode error = json(postJson("/api/orders", buyer, orderBody(productId, 1, offerId))
                    .andExpect(status().isConflict()));
            assertThat(error.get("code").asText()).as("offerId=%s", offerId).isEqualTo("INVALID_OFFER_SELECTION");
        }
        assertUnchanged(buyer, 200_000, productId, 3, 0);
    }

    @Test
    void F010_AC05_재고_5_단가_10000원_잔액_50000원에서_2개를_사면_재고_3이고_판매_중이다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 10_000, 5);
        charge(buyer, 50_000);

        JsonNode order = placeOrder(buyer, productId, 2, null);
        assertThat(order.get("quantity").asLong()).isEqualTo(2);
        assertThat(order.get("paidAmount").asLong()).isEqualTo(20_000);
        assertThat(balanceOf(buyer)).isEqualTo(30_000);
        assertThat(remainingQuantity(productId)).isEqualTo(3);
        assertThat(productStatus(productId)).isEqualTo("ON_SALE");
        assertThat(count("SELECT COUNT(*) FROM PRODUCT WHERE PRODUCT_ID = ? AND SOLD_AT IS NULL", productId)).isEqualTo(1);
        JsonNode publicDetail = json(getJson("/api/products/" + productId, null).andExpect(status().isOk()));
        assertThat(publicDetail.get("remainingQuantity").asLong()).isEqualTo(3);
        assertThat(publicDetail.get("initialQuantity").asLong()).isEqualTo(5);
    }

    @Test
    void F010_AC06_다른_구매자가_남은_3개를_사면_주문_2건_재고_0_판매_종료이고_추가_구매와_공개_조회가_막힌다() throws Exception {
        TestMember seller = member("seller");
        TestMember first = member("first");
        TestMember second = member("second");
        long productId = registerProduct(seller, 10_000, 5);
        charge(first, 50_000);
        charge(second, 50_000);
        long firstOrder = placeOrder(first, productId, 2, null).get("orderId").asLong();
        long secondOrder = placeOrder(second, productId, 3, null).get("orderId").asLong();

        assertThat(firstOrder).isNotEqualTo(secondOrder);
        assertThat(orderCount(productId)).isEqualTo(2);
        assertThat(remainingQuantity(productId)).isZero();
        assertThat(productStatus(productId)).isEqualTo("SOLD");
        assertThat(count("SELECT COUNT(*) FROM PRODUCT WHERE PRODUCT_ID = ? AND SOLD_AT IS NOT NULL", productId)).isEqualTo(1);
        assertThat(balanceOf(second)).isEqualTo(20_000);

        JsonNode rejected = json(postJson("/api/orders", first, orderBody(productId, 1, null)).andExpect(status().isConflict()));
        assertThat(rejected.get("code").asText()).isEqualTo("PRODUCT_NOT_ON_SALE");
        assertThat(balanceOf(first)).isEqualTo(30_000);
        getJson("/api/products/" + productId, null).andExpect(status().isNotFound());
        JsonNode list = json(getJson("/api/products", null));
        for (JsonNode item : list) {
            assertThat(item.get("productId").asLong()).isNotEqualTo(productId);
        }
    }

    @Test
    void F010_AC07_재고보다_많은_수량은_409_수량_입력_오류는_400이고_주문_잔액_재고가_그대로다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 10_000, 3);
        charge(buyer, 100_000);

        JsonNode stock = json(postJson("/api/orders", buyer, orderBody(productId, 4, null)).andExpect(status().isConflict()));
        assertThat(stock.get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
        for (Object quantity : new Object[] {0, -1, 1.5, null, 10_000_000_000L}) {
            JsonNode error = json(postJson("/api/orders", buyer, orderBody(productId, quantity, null))
                    .andExpect(status().isBadRequest()));
            assertThat(error.get("fieldErrors").has("quantity")).as("quantity=%s", quantity).isTrue();
        }
        assertUnchanged(buyer, 100_000, productId, 3, 0);
    }

    @Test
    void F010_AC08_합의_단가_8000원을_같은_구매자의_별도_주문에_다시_쓴다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 10_000, 5);
        long conversationId = startConversation(buyer, productId);
        long offerId = propose(buyer, conversationId, 8_000);
        respond(seller, offerId, true);
        charge(buyer, 30_000);

        JsonNode firstOrder = placeOrder(buyer, productId, 2, offerId);
        JsonNode secondOrder = placeOrder(buyer, productId, 1, offerId);

        assertThat(firstOrder.get("paidAmount").asLong()).isEqualTo(16_000);
        assertThat(secondOrder.get("paidAmount").asLong()).isEqualTo(8_000);
        assertThat(firstOrder.get("offerId").asLong()).isEqualTo(offerId);
        assertThat(secondOrder.get("offerId").asLong()).isEqualTo(offerId);
        assertThat(firstOrder.get("orderId").asLong()).isNotEqualTo(secondOrder.get("orderId").asLong());
        assertThat(offerStatus(offerId)).isEqualTo("ACCEPTED");
        assertThat(balanceOf(buyer)).isEqualTo(6_000);
        assertThat(remainingQuantity(productId)).isEqualTo(2);

        JsonNode orders = json(getJson("/api/conversations/" + conversationId, buyer)).get("orders");
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).get("orderId").asLong()).isEqualTo(secondOrder.get("orderId").asLong());
        assertThat(orders.get(1).get("orderId").asLong()).isEqualTo(firstOrder.get("orderId").asLong());
        assertThat(orders.get(1).get("quantity").asLong()).isEqualTo(2);
        assertThat(orders.get(1).get("unitPrice").asLong()).isEqualTo(8_000);
    }

    @Test
    void F009_AC02_다른_구매자의_결제로_재고가_0이_되면_대기_제안_응답과_수락_제안_결제가_모두_409이다() throws Exception {
        TestMember seller = member("seller");
        TestMember pendingBuyer = member("pending");
        TestMember acceptedBuyer = member("accepted");
        TestMember lastBuyer = member("last");
        long productId = registerProduct(seller, 50_000, 1);
        long pendingOffer = propose(pendingBuyer, startConversation(pendingBuyer, productId), 40_000);
        long acceptedOffer = propose(acceptedBuyer, startConversation(acceptedBuyer, productId), 45_000);
        respond(seller, acceptedOffer, true);
        charge(acceptedBuyer, 45_000);
        charge(lastBuyer, 50_000);

        placeOrder(lastBuyer, productId, 1, null);

        JsonNode respondError = json(postJson("/api/offers/" + pendingOffer + "/accept", seller, null)
                .andExpect(status().isConflict()));
        assertThat(respondError.get("code").asText()).isEqualTo("PRODUCT_NOT_ON_SALE");
        assertThat(offerStatus(pendingOffer)).isEqualTo("PENDING");

        JsonNode payError = json(postJson("/api/orders", acceptedBuyer, orderBody(productId, 1, acceptedOffer))
                .andExpect(status().isConflict()));
        assertThat(payError.get("code").asText()).isEqualTo("PRODUCT_NOT_ON_SALE");
        assertThat(balanceOf(acceptedBuyer)).isEqualTo(45_000);
        assertThat(orderCount(productId)).isEqualTo(1);
    }

    @Test
    void F009_다른_구매자_결제_후_재고가_남아_있으면_제안_응답과_합의_가격_구매를_계속_허용한다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        TestMember other = member("other");
        long productId = registerProduct(seller, 50_000, 3);
        long offerId = propose(buyer, startConversation(buyer, productId), 40_000);
        charge(other, 50_000);
        placeOrder(other, productId, 1, null);

        respond(seller, offerId, true);
        charge(buyer, 80_000);
        JsonNode order = placeOrder(buyer, productId, 2, offerId);
        assertThat(order.get("paidAmount").asLong()).isEqualTo(80_000);
        assertThat(remainingQuantity(productId)).isZero();
        assertThat(productStatus(productId)).isEqualTo("SOLD");
    }

    @Test
    void BR003_단가와_수량의_곱이_저장_범위나_long_범위를_넘으면_400이고_아무것도_바뀌지_않는다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 999_999_999_999_999L, 9_999_999_999L);
        charge(buyer, 999_999_999_999_999L);

        // 9.99e14 × 2 는 NUMBER(15) 를 넘는다.
        JsonNode overStorage = json(postJson("/api/orders", buyer, orderBody(productId, 2, null)).andExpect(status().isBadRequest()));
        assertThat(overStorage.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        // 9.99e14 × 9.99e9 는 long 곱셈 범위를 넘는다. 잘린 값이나 음수 총액을 저장하지 않는다.
        postJson("/api/orders", buyer, orderBody(productId, 9_999_999_999L, null)).andExpect(status().isBadRequest());

        assertThat(balanceOf(buyer)).isEqualTo(999_999_999_999_999L);
        assertThat(remainingQuantity(productId)).isEqualTo(9_999_999_999L);
        assertThat(orderCount(productId)).isZero();
    }
}
