package com.chatservice.marketplace.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.chatservice.marketplace.conversation.realtime.ConversationSessionRegistry;
import com.chatservice.marketplace.reservation.service.IReservationService;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.chatservice.marketplace.support.WsTestClient;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 판매자 예약 걸기·해제, 구매 가능 수량, 예약자 결제의 예약 종료, 만료 자동 해제, 수락과 예약(설계안 8.1).
 * 자동 처리는 스케줄러를 기다리지 않고 서비스 메서드를 직접 호출한다.
 * 동시 예약, 예약과 결제의 겹침, 해제·만료와 결제의 겹침은 후속 과제이며 여기서는 순차 요청만 본다.
 */
class ReservationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private IReservationService reservationService;
    @Autowired
    private ConversationSessionRegistry registry;

    private TestMember seller;
    private TestMember buyerA;
    private TestMember buyerB;
    private long productId;
    private long conversationA;
    private long conversationB;

    /** 남은 수량 5 인 상품과 구매 희망자 A·B 의 대화를 만든다. */
    private void prepare() throws Exception {
        seller = member("seller");
        buyerA = member("buyerA");
        buyerB = member("buyerB");
        productId = registerProduct(seller, 10_000, 5);
        conversationA = startConversation(buyerA, productId);
        conversationB = startConversation(buyerB, productId);
    }

    private ResultActions reserve(TestMember member, long conversationId, Object quantity) throws Exception {
        return postJson("/api/conversations/" + conversationId + "/reservations", member, Map.of("quantity", quantity));
    }

    private JsonNode reserveOk(TestMember member, long conversationId, long quantity) throws Exception {
        return json(reserve(member, conversationId, quantity).andExpect(status().isCreated()));
    }

    private ResultActions release(TestMember member, long reservationId) throws Exception {
        return mockMvc.perform(delete("/api/reservations/" + reservationId).cookie(member.cookie()));
    }

    private String reservationStatus(long reservationId) {
        return jdbcTemplate.queryForObject("SELECT STATUS FROM RESERVATION WHERE RESERVATION_ID = ?", String.class, reservationId);
    }

    private long reservationCount(String statusCondition) {
        return count("SELECT COUNT(*) FROM RESERVATION WHERE PRODUCT_ID = ?" + statusCondition, productId);
    }

    /** 상품 상세의 구매 가능 수량. viewer 가 null 이면 로그인 없는 조회다. */
    private long availableQuantityOfProduct(TestMember viewer) throws Exception {
        return json(getJson("/api/products/" + productId, viewer).andExpect(status().isOk())).get("availableQuantity").asLong();
    }

    private JsonNode conversationDetail(TestMember viewer, long conversationId) throws Exception {
        return json(getJson("/api/conversations/" + conversationId, viewer).andExpect(status().isOk()));
    }

    @Test
    void 예약을_걸면_활성_상태이고_만료_시각은_예약_시각의_20분_뒤이며_재고는_줄지_않는다() throws Exception {
        prepare();
        JsonNode reservation = reserveOk(seller, conversationA, 3);
        assertThat(reservation.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(reservation.get("quantity").asLong()).isEqualTo(3);
        assertThat(reservation.get("conversationId").asLong()).isEqualTo(conversationA);
        assertThat(reservation.get("productId").asLong()).isEqualTo(productId);
        Instant reservedAt = Instant.parse(reservation.get("reservedAt").asText());
        Instant expiresAt = Instant.parse(reservation.get("expiresAt").asText());
        assertThat(reservedAt).isEqualTo(clock.instant());
        // marketplace.reservation.duration = PT20M
        assertThat(Duration.between(reservedAt, expiresAt)).isEqualTo(Duration.ofMinutes(20));
        assertThat(reservation.get("endedAt").isNull()).isTrue();
        assertThat(reservation.get("releasedBy").isNull()).isTrue();
        assertThat(reservationCount("")).isEqualTo(1);
        // 예약은 재고를 차감하지 않는다.
        assertThat(remainingQuantity(productId)).isEqualTo(5);
        assertThat(productStatus(productId)).isEqualTo("ON_SALE");
    }

    @Test
    void 예약_수량의_상한은_남은_수량에서_다른_회원의_활성_예약을_뺀_값이다() throws Exception {
        prepare();
        reserveOk(seller, conversationA, 3);
        JsonNode error = json(reserve(seller, conversationB, 3).andExpect(status().isConflict()));
        assertThat(error.get("code").asText()).isEqualTo("RESERVATION_QUANTITY_EXCEEDED");
        assertThat(reservationCount("")).isEqualTo(1);
        reserveOk(seller, conversationB, 2);
        assertThat(reservationCount(" AND STATUS = 'ACTIVE'")).isEqualTo(2);
    }

    @Test
    void 같은_구매_희망자에게_활성_예약이_있으면_두_번째_예약은_409이다() throws Exception {
        prepare();
        reserveOk(seller, conversationA, 1);
        JsonNode error = json(reserve(seller, conversationA, 1).andExpect(status().isConflict()));
        assertThat(error.get("code").asText()).isEqualTo("RESERVATION_ALREADY_ACTIVE");
        assertThat(reservationCount("")).isEqualTo(1);
    }

    @Test
    void 구매_희망자는_403_제3자는_403_수량_0은_400_판매_종료_상품은_409이다() throws Exception {
        prepare();
        JsonNode byBuyer = json(reserve(buyerA, conversationA, 1).andExpect(status().isForbidden()));
        assertThat(byBuyer.get("code").asText()).isEqualTo("NOT_SELLER");
        JsonNode byOutsider = json(reserve(member("outsider"), conversationA, 1).andExpect(status().isForbidden()));
        assertThat(byOutsider.get("code").asText()).isEqualTo("NOT_CONVERSATION_MEMBER");
        JsonNode zero = json(reserve(seller, conversationA, 0).andExpect(status().isBadRequest()));
        assertThat(zero.get("fieldErrors").has("quantity")).isTrue();
        jdbcTemplate.update("UPDATE PRODUCT SET REMAINING_QUANTITY = 0, STATUS = 'SOLD' WHERE PRODUCT_ID = ?", productId);
        JsonNode sold = json(reserve(seller, conversationA, 1).andExpect(status().isConflict()));
        assertThat(sold.get("code").asText()).isEqualTo("PRODUCT_NOT_ON_SALE");
        assertThat(reservationCount("")).isZero();
    }

    @Test
    void 예약_중의_구매_가능_수량은_조회한_회원에_따라_다르고_대화_상세에_예약이_담긴다() throws Exception {
        prepare();
        long reservationId = reserveOk(seller, conversationA, 3).get("reservationId").asLong();

        JsonNode detailA = conversationDetail(buyerA, conversationA);
        assertThat(detailA.get("reservation").get("reservationId").asLong()).isEqualTo(reservationId);
        assertThat(detailA.get("reservation").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(detailA.get("product").get("remainingQuantity").asLong()).isEqualTo(5);
        // 예약된 구매 희망자 본인에게는 자기 예약을 빼지 않는다.
        assertThat(detailA.get("product").get("availableQuantity").asLong()).isEqualTo(5);
        // 판매자에게는 다른 회원의 예약을 모두 뺀 값이고, 이것이 더 걸 수 있는 예약 수량이다.
        assertThat(conversationDetail(seller, conversationA).get("product").get("availableQuantity").asLong()).isEqualTo(2);
        JsonNode detailB = conversationDetail(buyerB, conversationB);
        assertThat(detailB.get("reservation").isNull()).isTrue();
        assertThat(detailB.get("product").get("availableQuantity").asLong()).isEqualTo(2);

        assertThat(availableQuantityOfProduct(null)).isEqualTo(2);
        assertThat(availableQuantityOfProduct(buyerA)).isEqualTo(5);
        assertThat(availableQuantityOfProduct(buyerB)).isEqualTo(2);
        JsonNode listed = null;
        for (JsonNode item : json(getJson("/api/products", null).andExpect(status().isOk()))) {
            if (item.get("productId").asLong() == productId) {
                listed = item;
            }
        }
        assertThat(listed).isNotNull();
        assertThat(listed.get("remainingQuantity").asLong()).isEqualTo(5);
        assertThat(listed.get("availableQuantity").asLong()).isEqualTo(2);
    }

    @Test
    void 다른_회원의_결제는_구매_가능_수량까지만_허용되고_예약자의_결제는_예약을_끝낸다() throws Exception {
        prepare();
        long reservationId = reserveOk(seller, conversationA, 3).get("reservationId").asLong();
        charge(buyerA, 50_000);
        charge(buyerB, 50_000);

        JsonNode reserved = json(postJson("/api/orders", buyerB, orderBody(productId, 3, null)).andExpect(status().isConflict()));
        assertThat(reserved.get("code").asText()).isEqualTo("PRODUCT_RESERVED");
        assertThat(orderCount(productId)).isZero();
        assertThat(remainingQuantity(productId)).isEqualTo(5);
        assertThat(balanceOf(buyerB)).isEqualTo(50_000);

        placeOrder(buyerB, productId, 2, null);
        assertThat(remainingQuantity(productId)).isEqualTo(3);
        assertThat(reservationStatus(reservationId)).isEqualTo("ACTIVE");

        long orderId = placeOrder(buyerA, productId, 3, null).get("orderId").asLong();
        assertThat(remainingQuantity(productId)).isZero();
        assertThat(productStatus(productId)).isEqualTo("SOLD");
        assertThat(reservationStatus(reservationId)).isEqualTo("FULFILLED");
        assertThat(jdbcTemplate.queryForObject("SELECT FULFILLED_ORDER_ID FROM RESERVATION WHERE RESERVATION_ID = ?",
                Long.class, reservationId)).isEqualTo(orderId);
        JsonNode detail = conversationDetail(buyerA, conversationA);
        assertThat(detail.get("reservation").get("status").asText()).isEqualTo("FULFILLED");
        assertThat(detail.get("reservation").get("fulfilledOrderId").asLong()).isEqualTo(orderId);
        assertThat(detail.get("reservation").get("endedAt").isNull()).isFalse();
    }

    @Test
    void 예약_수량보다_적게_결제해도_예약은_끝난다() throws Exception {
        prepare();
        long reservationId = reserveOk(seller, conversationA, 3).get("reservationId").asLong();
        charge(buyerA, 10_000);
        placeOrder(buyerA, productId, 1, null);
        assertThat(reservationStatus(reservationId)).isEqualTo("FULFILLED");
        // 예약이 끝났으므로 다른 회원의 구매 가능 수량은 남은 수량 4 와 같다.
        assertThat(availableQuantityOfProduct(buyerB)).isEqualTo(4);
    }

    @Test
    void 판매자와_구매_희망자는_해제할_수_있고_제3자는_403_끝난_예약은_409_없는_예약은_404이다() throws Exception {
        prepare();
        long first = reserveOk(seller, conversationA, 3).get("reservationId").asLong();
        JsonNode byOutsider = json(release(member("outsider"), first).andExpect(status().isForbidden()));
        assertThat(byOutsider.get("code").asText()).isEqualTo("NOT_CONVERSATION_MEMBER");
        assertThat(reservationStatus(first)).isEqualTo("ACTIVE");

        JsonNode bySeller = json(release(seller, first).andExpect(status().isOk()));
        assertThat(bySeller.get("status").asText()).isEqualTo("RELEASED");
        assertThat(bySeller.get("releasedBy").asText()).isEqualTo("SELLER");
        assertThat(bySeller.get("endedAt").isNull()).isFalse();
        JsonNode again = json(release(seller, first).andExpect(status().isConflict()));
        assertThat(again.get("code").asText()).isEqualTo("RESERVATION_NOT_ACTIVE");

        long second = reserveOk(seller, conversationA, 3).get("reservationId").asLong();
        JsonNode byBuyer = json(release(buyerA, second).andExpect(status().isOk()));
        assertThat(byBuyer.get("releasedBy").asText()).isEqualTo("BUYER");
        assertThat(reservationStatus(second)).isEqualTo("RELEASED");

        JsonNode missing = json(release(seller, 999_999_999L).andExpect(status().isNotFound()));
        assertThat(missing.get("code").asText()).isEqualTo("RESERVATION_NOT_FOUND");

        // 해제 뒤에는 다른 회원이 남은 수량 전체를 살 수 있다.
        assertThat(availableQuantityOfProduct(buyerB)).isEqualTo(5);
        charge(buyerB, 50_000);
        placeOrder(buyerB, productId, 5, null);
        assertThat(remainingQuantity(productId)).isZero();
    }

    @Test
    void 만료_시각이_지난_활성_예약만_자동_해제되고_만료_뒤의_결제는_예약을_끝내지_않는다() throws Exception {
        prepare();
        long reservationId = reserveOk(seller, conversationA, 2).get("reservationId").asLong();

        clock.advance(Duration.ofMinutes(19).plusSeconds(59));
        assertThat(reservationService.expireReservations(clock.instant())).isZero();
        assertThat(reservationStatus(reservationId)).isEqualTo("ACTIVE");
        assertThat(availableQuantityOfProduct(buyerB)).isEqualTo(3);

        clock.advance(Duration.ofSeconds(1));
        // 만료 시각이 지났지만 아직 자동 해제가 실행되지 않은 예약은 다른 회원을 막지 않고, 예약자의 결제도 예약으로 인정하지 않는다.
        assertThat(availableQuantityOfProduct(buyerB)).isEqualTo(5);
        charge(buyerA, 10_000);
        placeOrder(buyerA, productId, 1, null);
        assertThat(reservationStatus(reservationId)).isEqualTo("ACTIVE");

        assertThat(reservationService.expireReservations(clock.instant())).isEqualTo(1);
        assertThat(reservationStatus(reservationId)).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("SELECT ENDED_AT FROM RESERVATION WHERE RESERVATION_ID = ?", Object.class, reservationId))
                .isNotNull();
        // 다시 실행해도 대상이 없다.
        assertThat(reservationService.expireReservations(clock.instant())).isZero();
        JsonNode detail = conversationDetail(buyerA, conversationA);
        assertThat(detail.get("reservation").get("status").asText()).isEqualTo("EXPIRED");
    }

    @Test
    void 수락_요청에_예약을_함께_보내면_수락과_예약이_한_번에_되고_본문이_없으면_수락만_된다() throws Exception {
        prepare();
        long offerA = propose(buyerA, conversationA, 8_000);
        JsonNode accepted = json(postJson("/api/offers/" + offerA + "/accept", seller, Map.of("reserve", true, "quantity", 2))
                .andExpect(status().isOk()));
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(accepted.get("reservation").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(accepted.get("reservation").get("quantity").asLong()).isEqualTo(2);
        assertThat(accepted.get("reservation").get("conversationId").asLong()).isEqualTo(conversationA);
        assertThat(offerStatus(offerA)).isEqualTo("ACCEPTED");
        assertThat(reservationCount(" AND STATUS = 'ACTIVE'")).isEqualTo(1);

        long offerB = propose(buyerB, conversationB, 8_000);
        JsonNode plain = json(postJson("/api/offers/" + offerB + "/accept", seller, null).andExpect(status().isOk()));
        assertThat(plain.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(plain.get("reservation").isNull()).isTrue();
        assertThat(reservationCount("")).isEqualTo(1);
    }

    @Test
    void 수락과_예약에서_예약이_거절되면_수락도_반영되지_않는다() throws Exception {
        prepare();
        reserveOk(seller, conversationA, 3);
        long offerB = propose(buyerB, conversationB, 8_000);
        // 남은 수량 5 에서 A 의 예약 3 을 뺀 2 가 상한이므로 3 은 거절되고, 같은 트랜잭션의 수락도 롤백된다.
        JsonNode exceeded = json(postJson("/api/offers/" + offerB + "/accept", seller, Map.of("reserve", true, "quantity", 3))
                .andExpect(status().isConflict()));
        assertThat(exceeded.get("code").asText()).isEqualTo("RESERVATION_QUANTITY_EXCEEDED");
        assertThat(offerStatus(offerB)).isEqualTo("PENDING");
        assertThat(reservationCount("")).isEqualTo(1);
        // reserve 가 true 인데 수량이 없으면 400 이고 수락되지 않는다.
        JsonNode missing = json(postJson("/api/offers/" + offerB + "/accept", seller, Map.of("reserve", true))
                .andExpect(status().isBadRequest()));
        assertThat(missing.get("fieldErrors").has("quantity")).isTrue();
        assertThat(offerStatus(offerB)).isEqualTo("PENDING");
    }

    @Test
    void 예약을_걸면_상품의_다른_대화_세션이_구매_가능_수량이_담긴_CONVERSATION_STATE를_받는다() throws Exception {
        prepare();
        WsTestClient.Connection buyerBWs = WsTestClient.connect(port, buyerB, "?conversationId=" + conversationB);
        long deadline = System.currentTimeMillis() + 5_000;
        while (!registry.sessionsOf(conversationB).containsKey(buyerB.id()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        try {
            reserveOk(seller, conversationA, 3);
            JsonNode event = objectMapper.readTree(buyerBWs.next(5_000));
            assertThat(event.get("type").asText()).isEqualTo("CONVERSATION_STATE");
            assertThat(event.get("conversationId").asLong()).isEqualTo(conversationB);
            assertThat(event.get("remainingQuantity").asLong()).isEqualTo(5);
            assertThat(event.get("availableQuantity").asLong()).isEqualTo(2);
            assertThat(event.get("writable").asBoolean()).isTrue();
        } finally {
            buyerBWs.close();
        }
    }
}
