package com.chatservice.marketplace.offer;

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
 * F-008 가격 제안, F-009 수락·거절, F-007-AC-02(설계 9장).
 * 다른 구매자의 실제 결제로 재고가 0 이 된 경우(F-009-AC-02)와 합의 가격 결제(F-009-AC-01)는
 * 주문 기능이 필요하므로 OrderPaymentIntegrationTest 에서 확인한다. 동시 제안·동시 응답은 후속 과제다.
 */
class OfferIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ConversationSessionRegistry registry;

    private TestMember seller;
    private TestMember buyer;
    private long productId;
    private long conversationId;

    private void prepare() throws Exception {
        seller = member("seller");
        buyer = member("buyer");
        productId = registerProduct(seller, 50_000, 1);
        conversationId = startConversation(buyer, productId);
    }

    private long offerCount() {
        return count("SELECT COUNT(*) FROM PRICE_OFFER WHERE CONVERSATION_ID = ?", conversationId);
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
                    Map.of("amount", 40_000)).andExpect(status().isCreated()));
            assertThat(offer.get("status").asText()).isEqualTo("PENDING");
            assertThat(offer.get("amount").asLong()).isEqualTo(40_000);

            JsonNode event = objectMapper.readTree(sellerWs.next(5_000));
            assertThat(event.get("type").asText()).isEqualTo("OFFER");
            assertThat(event.get("offerId").asLong()).isEqualTo(offer.get("offerId").asLong());
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
                Map.of("amount", 45_000)).andExpect(status().isConflict()));
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
                    Map.of("amount", amount)).andExpect(status().isBadRequest()));
            assertThat(error.get("code").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(error.get("fieldErrors").has("amount")).isTrue();
        }
        for (Object amount : new Object[] {0, -1, 1.5}) {
            postJson("/api/conversations/" + conversationId + "/offers", buyer, Map.of("amount", amount))
                    .andExpect(status().isBadRequest());
        }
        JsonNode bySeller = json(postJson("/api/conversations/" + conversationId + "/offers", seller,
                Map.of("amount", 40_000)).andExpect(status().isForbidden()));
        assertThat(bySeller.get("code").asText()).isEqualTo("NOT_BUYER");
        JsonNode byOutsider = json(postJson("/api/conversations/" + conversationId + "/offers", member("outsider"),
                Map.of("amount", 40_000)).andExpect(status().isForbidden()));
        assertThat(byOutsider.get("code").asText()).isEqualTo("NOT_CONVERSATION_MEMBER");
        assertThat(offerCount()).isZero();

        long accepted = propose(buyer, conversationId, 40_000);
        respond(seller, accepted, true);
        JsonNode renegotiate = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                Map.of("amount", 35_000)).andExpect(status().isConflict()));
        assertThat(renegotiate.get("code").asText()).isEqualTo("OFFER_ALREADY_ACCEPTED");
        assertThat(offerCount()).isEqualTo(1);
    }

    @Test
    void F008_판매_종료_상품에는_제안할_수_없다() throws Exception {
        prepare();
        jdbcTemplate.update("UPDATE PRODUCT SET REMAINING_QUANTITY = 0, STATUS = 'SOLD' WHERE PRODUCT_ID = ?", productId);
        JsonNode error = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                Map.of("amount", 40_000)).andExpect(status().isConflict()));
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
