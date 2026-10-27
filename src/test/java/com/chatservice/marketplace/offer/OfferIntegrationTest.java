package com.chatservice.marketplace.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.conversation.realtime.ConversationSessionRegistry;
import com.chatservice.marketplace.order.service.IShipmentService;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.chatservice.marketplace.support.WsTestClient;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * F-008 가격 제안, F-009 수락·거절, F-007-AC-02(설계 9장), 제안 수량과 합의 상태(가격 제안 수량 설계안 8.2).
 * 다른 구매자의 실제 결제로 재고가 0 이 된 경우(F-009-AC-02)와 합의 가격 결제(F-009-AC-01)는
 * 주문 기능이 필요하므로 OrderPaymentIntegrationTest 에서 확인한다. 동시 제안·동시 응답은 후속 과제다.
 */
class OfferIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ConversationSessionRegistry registry;
    @Autowired
    private IShipmentService shipmentService;

    private TestMember seller;
    private TestMember buyer;
    private long productId;
    private long conversationId;

    /** 등록 가격 50,000원, 남은 수량 1 인 상품과 구매 희망자의 대화를 만든다. */
    private void prepare() throws Exception {
        prepare(1);
    }

    private void prepare(long quantity) throws Exception {
        seller = member("seller");
        buyer = member("buyer");
        productId = registerProduct(seller, 50_000, quantity);
        conversationId = startConversation(buyer, productId);
    }

    private long offerCount() {
        return count("SELECT COUNT(*) FROM PRICE_OFFER WHERE CONVERSATION_ID = ?", conversationId);
    }

    private Map<String, Object> offerBody(Object amount, Object quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        if (quantity != null) {
            body.put("quantity", quantity);
        }
        return body;
    }

    /** 판매자가 수락하고 수락 응답을 돌려준다. 응답의 reservation 에 함께 걸린 예약이 있다. */
    private JsonNode accept(long offerId) throws Exception {
        return json(postJson("/api/offers/" + offerId + "/accept", seller, null).andExpect(status().isOk()));
    }

    private JsonNode offerItem(TestMember viewer, long offerId) throws Exception {
        for (JsonNode offer : json(getJson("/api/conversations/" + conversationId, viewer).andExpect(status().isOk())).get("offers")) {
            if (offer.get("offerId").asLong() == offerId) {
                return offer;
            }
        }
        throw new AssertionError("대화 상세에 제안이 없습니다. offerId=" + offerId);
    }

    @Test
    void F008_AC01_등록가_50000원에_40000원을_제안하면_응답_대기가_되고_판매자_세션이_OFFER를_받는다() throws Exception {
        prepare();
        WsTestClient.Connection sellerWs = WsTestClient.connect(port, seller, "?conversationId=" + conversationId);
        long deadline = System.currentTimeMillis() + 5_000;
        while (!registry.sessionsOf(conversationId).containsKey(seller.id()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        try {
            JsonNode offer = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                    offerBody(40_000, 1)).andExpect(status().isCreated()));
            assertThat(offer.get("status").asText()).isEqualTo("PENDING");
            assertThat(offer.get("amount").asLong()).isEqualTo(40_000);

            JsonNode event = objectMapper.readTree(sellerWs.next(5_000));
            assertThat(event.get("type").asText()).isEqualTo("OFFER");
            assertThat(event.get("offerId").asLong()).isEqualTo(offer.get("offerId").asLong());
            assertThat(event.get("quantity").asLong()).isEqualTo(1);
            assertThat(event.get("status").asText()).isEqualTo("PENDING");
        } finally {
            sellerWs.close();
        }
    }

    @Test
    void F008_AC02_응답_대기_제안이_있으면_새_제안은_409이다() throws Exception {
        prepare();
        propose(buyer, conversationId, 40_000);
        JsonNode error = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                offerBody(45_000, 1)).andExpect(status().isConflict()));
        assertThat(error.get("code").asText()).isEqualTo("OFFER_PENDING_EXISTS");
        assertThat(offerCount()).isEqualTo(1);
    }

    @Test
    void F008_AC03_직전_제안이_거절되면_새_제안을_할_수_있다() throws Exception {
        prepare();
        long first = propose(buyer, conversationId, 30_000);
        respond(seller, first, false);
        assertThat(offerStatus(first)).isEqualTo("REJECTED");

        long second = propose(buyer, conversationId, 45_000);
        assertThat(second).isNotEqualTo(first);
        assertThat(offerStatus(second)).isEqualTo("PENDING");
        // 거절된 제안은 그대로 남고 별도의 제안이 만들어진다.
        assertThat(offerStatus(first)).isEqualTo("REJECTED");
    }

    @Test
    void F008_등록가_이상_금액은_400_판매자_제안은_403_수락_제안이_있으면_409이다() throws Exception {
        prepare();
        for (long amount : new long[] {50_000, 60_000}) {
            JsonNode error = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                    offerBody(amount, 1)).andExpect(status().isBadRequest()));
            assertThat(error.get("code").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(error.get("fieldErrors").has("amount")).isTrue();
        }
        for (Object amount : new Object[] {0, -1, 1.5}) {
            postJson("/api/conversations/" + conversationId + "/offers", buyer, offerBody(amount, 1))
                    .andExpect(status().isBadRequest());
        }
        JsonNode bySeller = json(postJson("/api/conversations/" + conversationId + "/offers", seller,
                offerBody(40_000, 1)).andExpect(status().isForbidden()));
        assertThat(bySeller.get("code").asText()).isEqualTo("NOT_BUYER");
        JsonNode byOutsider = json(postJson("/api/conversations/" + conversationId + "/offers", member("outsider"),
                offerBody(40_000, 1)).andExpect(status().isForbidden()));
        assertThat(byOutsider.get("code").asText()).isEqualTo("NOT_CONVERSATION_MEMBER");
        assertThat(offerCount()).isZero();

        long accepted = propose(buyer, conversationId, 40_000);
        respond(seller, accepted, true);
        JsonNode renegotiate = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                offerBody(35_000, 1)).andExpect(status().isConflict()));
        assertThat(renegotiate.get("code").asText()).isEqualTo("OFFER_ALREADY_ACCEPTED");
        assertThat(offerCount()).isEqualTo(1);
    }

    @Test
    void F008_판매_종료_상품에는_제안할_수_없다() throws Exception {
        prepare();
        jdbcTemplate.update("UPDATE PRODUCT SET REMAINING_QUANTITY = 0, STATUS = 'SOLD' WHERE PRODUCT_ID = ?", productId);
        JsonNode error = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                offerBody(40_000, 1)).andExpect(status().isConflict()));
        assertThat(error.get("code").asText()).isEqualTo("PRODUCT_NOT_ON_SALE");
        assertThat(offerCount()).isZero();
    }

    @Test
    void F008_일반_메시지의_가격_언급은_제안이_아니다() throws Exception {
        prepare();
        sendMessage(buyer, conversationId, "40000원에 가능할까요?");
        assertThat(offerCount()).isZero();
    }

    @Test
    void F008_제안_수량이_없거나_0이면_400이고_fieldErrors에_quantity가_있다() throws Exception {
        prepare();
        for (Object quantity : new Object[] {null, 0, -1}) {
            JsonNode error = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                    offerBody(40_000, quantity)).andExpect(status().isBadRequest()));
            assertThat(error.get("code").asText()).as("quantity=%s", quantity).isEqualTo("VALIDATION_ERROR");
            assertThat(error.get("fieldErrors").has("quantity")).as("quantity=%s", quantity).isTrue();
        }
        assertThat(offerCount()).isZero();
    }

    @Test
    void F008_제안_수량이_구매_가능_수량보다_많으면_409_OFFER_QUANTITY_EXCEEDED이다() throws Exception {
        prepare(5);
        JsonNode over = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                offerBody(40_000, 6)).andExpect(status().isConflict()));
        assertThat(over.get("code").asText()).isEqualTo("OFFER_QUANTITY_EXCEEDED");
        assertThat(offerCount()).isZero();

        // 다른 구매 희망자에게 3개가 예약되면 이 구매 희망자의 구매 가능 수량은 2 다.
        TestMember other = member("other");
        long otherOffer = propose(other, startConversation(other, productId), 40_000, 3);
        respond(seller, otherOffer, true);
        JsonNode overReserved = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                offerBody(40_000, 3)).andExpect(status().isConflict()));
        assertThat(overReserved.get("code").asText()).isEqualTo("OFFER_QUANTITY_EXCEEDED");
        assertThat(offerCount()).isZero();

        propose(buyer, conversationId, 40_000, 2);
        assertThat(offerCount()).isEqualTo(1);
    }

    @Test
    void F008_제안_응답과_대화_상세에_수량이_있고_수락된_제안의_합의_상태는_RESERVED이다() throws Exception {
        prepare(5);
        JsonNode offer = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                offerBody(40_000, 3)).andExpect(status().isCreated()));
        long offerId = offer.get("offerId").asLong();
        assertThat(offer.get("quantity").asLong()).isEqualTo(3);
        assertThat(offer.get("reservation").isNull()).isTrue();

        JsonNode pending = offerItem(buyer, offerId);
        assertThat(pending.get("quantity").asLong()).isEqualTo(3);
        assertThat(pending.get("agreementStatus").isNull()).isTrue();

        accept(offerId);
        assertThat(offerItem(buyer, offerId).get("agreementStatus").asText()).isEqualTo("RESERVED");
        assertThat(offerItem(seller, offerId).get("agreementStatus").asText()).isEqualTo("RESERVED");
    }

    @Test
    void F009_수락하면_제안_수량만큼_예약이_걸리고_수락_응답에_그_예약이_담긴다() throws Exception {
        prepare(5);
        long offerId = propose(buyer, conversationId, 40_000, 3);
        JsonNode accepted = accept(offerId);
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(accepted.get("quantity").asLong()).isEqualTo(3);
        JsonNode reservation = accepted.get("reservation");
        assertThat(reservation.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(reservation.get("quantity").asLong()).isEqualTo(3);
        assertThat(reservation.get("offerId").asLong()).isEqualTo(offerId);
        assertThat(reservation.get("conversationId").asLong()).isEqualTo(conversationId);
    }

    @Test
    void F008_합의가_예약_중이거나_결제됨이면_새_제안은_409이다() throws Exception {
        prepare(5);
        long offerId = propose(buyer, conversationId, 40_000, 2);
        accept(offerId);
        JsonNode whileReserved = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                offerBody(35_000, 1)).andExpect(status().isConflict()));
        assertThat(whileReserved.get("code").asText()).isEqualTo("OFFER_ALREADY_ACCEPTED");

        charge(buyer, 80_000);
        placeOrder(buyer, productId, 2, offerId);
        assertThat(offerItem(buyer, offerId).get("agreementStatus").asText()).isEqualTo("ORDERED");
        JsonNode whileOrdered = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                offerBody(35_000, 1)).andExpect(status().isConflict()));
        assertThat(whileOrdered.get("code").asText()).isEqualTo("OFFER_ALREADY_ACCEPTED");
        assertThat(offerCount()).isEqualTo(1);
    }

    @Test
    void F008_예약이_만료되면_합의는_EXPIRED이고_새_제안을_할_수_있다() throws Exception {
        prepare(5);
        long offerId = propose(buyer, conversationId, 40_000, 2);
        accept(offerId);
        // 만료 자동 해제가 실행되기 전이라도 만료 시각이 지나면 합의는 만료다.
        clock.advance(Duration.ofMinutes(20));
        assertThat(offerItem(buyer, offerId).get("agreementStatus").asText()).isEqualTo("EXPIRED");
        long next = propose(buyer, conversationId, 35_000, 1);
        assertThat(offerStatus(next)).isEqualTo("PENDING");
    }

    @Test
    void F008_판매자가_예약을_해제하면_합의는_RELEASED이고_새_제안을_할_수_있다() throws Exception {
        prepare(5);
        long offerId = propose(buyer, conversationId, 40_000, 2);
        long reservationId = accept(offerId).get("reservation").get("reservationId").asLong();
        mockMvc.perform(delete("/api/reservations/" + reservationId).cookie(seller.cookie())).andExpect(status().isOk());
        assertThat(offerItem(buyer, offerId).get("agreementStatus").asText()).isEqualTo("RELEASED");
        long next = propose(buyer, conversationId, 35_000, 1);
        assertThat(offerStatus(next)).isEqualTo("PENDING");
    }

    @Test
    void F008_합의_가격_주문이_정상_완료되면_합의는_EXPIRED이고_새_제안을_할_수_있다() throws Exception {
        prepare(5);
        long offerId = propose(buyer, conversationId, 40_000, 2);
        accept(offerId);
        charge(buyer, 80_000);
        long orderId = placeOrder(buyer, productId, 2, offerId).get("orderId").asLong();

        // 발송하고 모의 배송 기간 2분이 지나 배송 완료로 만든 뒤 구매자가 수령을 확인한다.
        ship(seller, orderId);
        Instant deliveredAt = clock.instant().plus(Duration.ofMinutes(2));
        clock.setInstant(deliveredAt);
        assertThat(shipmentService.completeDueDeliveries(deliveredAt)).isEqualTo(1);
        postJson("/api/orders/" + orderId + "/confirm-receipt", buyer, null).andExpect(status().isOk());
        assertThat(tradeStatus(orderId)).isEqualTo("COMPLETED");

        assertThat(offerItem(buyer, offerId).get("agreementStatus").asText()).isEqualTo("EXPIRED");
        long next = propose(buyer, conversationId, 35_000, 1);
        assertThat(offerStatus(next)).isEqualTo("PENDING");
    }

    @Test
    void F009_이미_응답한_제안은_409_판매자가_아니면_403_없으면_404이고_결과가_바뀌지_않는다() throws Exception {
        prepare();
        long offerId = propose(buyer, conversationId, 40_000);
        JsonNode byBuyer = json(postJson("/api/offers/" + offerId + "/accept", buyer, null).andExpect(status().isForbidden()));
        assertThat(byBuyer.get("code").asText()).isEqualTo("NOT_SELLER");
        assertThat(offerStatus(offerId)).isEqualTo("PENDING");

        JsonNode accepted = json(postJson("/api/offers/" + offerId + "/accept", seller, null).andExpect(status().isOk()));
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(accepted.get("respondedAt").isNull()).isFalse();

        JsonNode again = json(postJson("/api/offers/" + offerId + "/reject", seller, null).andExpect(status().isConflict()));
        assertThat(again.get("code").asText()).isEqualTo("OFFER_ALREADY_RESPONDED");
        assertThat(offerStatus(offerId)).isEqualTo("ACCEPTED");

        JsonNode missing = json(postJson("/api/offers/999999999/accept", seller, null).andExpect(status().isNotFound()));
        assertThat(missing.get("code").asText()).isEqualTo("OFFER_NOT_FOUND");
    }

    @Test
    void F009_수락해도_공개_등록_가격과_재고는_바뀌지_않는다() throws Exception {
        prepare();
        respond(seller, propose(buyer, conversationId, 40_000), true);
        JsonNode product = json(getJson("/api/products/" + productId, null).andExpect(status().isOk()));
        assertThat(product.get("price").asLong()).isEqualTo(50_000);
        assertThat(product.get("remainingQuantity").asLong()).isEqualTo(1);
        assertThat(product.get("status").asText()).isEqualTo("ON_SALE");
        // 수락이 제안 수량 1개를 예약하므로 로그인하지 않은 조회의 구매 가능 수량은 0 이다.
        assertThat(product.get("availableQuantity").asLong()).isZero();
    }

    @Test
    void F009_판매_종료_뒤에는_대기_제안에_응답할_수_없고_제안은_PENDING_으로_남는다() throws Exception {
        prepare();
        long offerId = propose(buyer, conversationId, 40_000);
        jdbcTemplate.update("UPDATE PRODUCT SET REMAINING_QUANTITY = 0, STATUS = 'SOLD' WHERE PRODUCT_ID = ?", productId);
        JsonNode error = json(postJson("/api/offers/" + offerId + "/accept", seller, null).andExpect(status().isConflict()));
        assertThat(error.get("code").asText()).isEqualTo("PRODUCT_NOT_ON_SALE");
        assertThat(offerStatus(offerId)).isEqualTo("PENDING");
    }

    @Test
    void F007_AC02_연결이_끊긴_동안_수락된_제안을_재접속_후_상세에서_확인한다() throws Exception {
        prepare();
        long offerId = propose(buyer, conversationId, 40_000);
        respond(seller, offerId, true);

        JsonNode detail = json(getJson("/api/conversations/" + conversationId, buyer).andExpect(status().isOk()));
        JsonNode offers = detail.get("offers");
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).get("offerId").asLong()).isEqualTo(offerId);
        assertThat(offers.get(0).get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(offers.get(0).get("respondedAt").isNull()).isFalse();
    }
}
