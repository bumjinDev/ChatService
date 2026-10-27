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
import com.chatservice.marketplace.order.service.IShipmentService;
import com.chatservice.marketplace.reservation.service.IReservationService;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.chatservice.marketplace.support.WsTestClient;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 가격 제안 수락으로 걸리는 예약, 구매 가능 수량, 합의 가격 결제의 예약 종료, 취소 뒤 새 예약, 해제, 만료 자동 해제
 * (예약 기능 설계안 8.1, 가격 제안 수량 설계안 8.3). 예약은 구매 희망자의 제안을 판매자가 수락해 만든다.
 * 자동 처리는 스케줄러를 기다리지 않고 서비스 메서드를 직접 호출한다.
 * 동시 예약, 예약과 결제의 겹침, 해제·만료와 결제의 겹침은 후속 과제이며 여기서는 순차 요청만 본다.
 */
class ReservationIntegrationTest extends IntegrationTestSupport {

    /** 제안 금액. 등록 가격 10,000원보다 작다. */
    private static final long OFFER_AMOUNT = 8_000;

    @Autowired
    private IReservationService reservationService;
    @Autowired
    private IShipmentService shipmentService;
    @Autowired
    private ConversationSessionRegistry registry;

    private TestMember seller;
    private TestMember buyerA;
    private TestMember buyerB;
    private long productId;
    private long conversationA;
    private long conversationB;

    /** 등록 가격 10,000원, 남은 수량 5 인 상품과 구매 희망자 A·B 의 대화를 만든다. */
    private void prepare() throws Exception {
        seller = member("seller");
        buyerA = member("buyerA");
        buyerB = member("buyerB");
        productId = registerProduct(seller, 10_000, 5);
        conversationA = startConversation(buyerA, productId);
        conversationB = startConversation(buyerB, productId);
    }

    private ResultActions acceptRequest(long offerId) throws Exception {
        return postJson("/api/offers/" + offerId + "/accept", seller, null);
    }

    /** 구매 희망자가 quantity 개를 제안하고 판매자가 수락한다. 수락 응답을 돌려주며, 응답의 reservation 이 함께 걸린 예약이다. */
    private JsonNode reserveByOffer(TestMember buyer, long conversationId, long quantity) throws Exception {
        long offerId = propose(buyer, conversationId, OFFER_AMOUNT, quantity);
        return json(acceptRequest(offerId).andExpect(status().isOk()));
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
    void 수락하면_예약이_활성이고_수량은_제안_수량이며_만료_시각은_20분_뒤이고_OFFER_ID가_그_제안이다() throws Exception {
        prepare();
        JsonNode accepted = reserveByOffer(buyerA, conversationA, 3);
        long offerId = accepted.get("offerId").asLong();
        JsonNode reservation = accepted.get("reservation");
        assertThat(reservation.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(reservation.get("quantity").asLong()).isEqualTo(3);
        assertThat(reservation.get("offerId").asLong()).isEqualTo(offerId);
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
        assertThat(jdbcTemplate.queryForObject("SELECT OFFER_ID FROM RESERVATION WHERE RESERVATION_ID = ?", Long.class,
                reservation.get("reservationId").asLong())).isEqualTo(offerId);
        // 예약은 재고를 차감하지 않는다.
        assertThat(remainingQuantity(productId)).isEqualTo(5);
        assertThat(productStatus(productId)).isEqualTo("ON_SALE");
    }

    @Test
    void 제안_수량은_구매_가능_수량까지이고_수락_전에_예약할_수_있는_수량이_줄면_수락은_409이고_제안은_PENDING으로_남는다() throws Exception {
        prepare();
        reserveByOffer(buyerA, conversationA, 3);
        JsonNode over = json(postJson("/api/conversations/" + conversationB + "/offers", buyerB,
                Map.of("amount", OFFER_AMOUNT, "quantity", 3)).andExpect(status().isConflict()));
        assertThat(over.get("code").asText()).isEqualTo("OFFER_QUANTITY_EXCEEDED");
        long offerB = propose(buyerB, conversationB, OFFER_AMOUNT, 2);

        // 다른 회원이 등록 가격으로 1개를 사면 남은 수량 4 에서 A 의 예약 3 을 뺀 1 개만 B 에게 예약할 수 있다.
        TestMember other = member("other");
        charge(other, 10_000);
        placeOrder(other, productId, 1, null);
        JsonNode exceeded = json(acceptRequest(offerB).andExpect(status().isConflict()));
        assertThat(exceeded.get("code").asText()).isEqualTo("RESERVATION_QUANTITY_EXCEEDED");
        // 같은 트랜잭션의 수락도 롤백된다.
        assertThat(offerStatus(offerB)).isEqualTo("PENDING");
        assertThat(reservationCount("")).isEqualTo(1);
    }

    @Test
    void 예약_중의_구매_가능_수량은_조회한_회원에_따라_다르고_대화_상세에_예약이_담긴다() throws Exception {
        prepare();
        JsonNode accepted = reserveByOffer(buyerA, conversationA, 3);
        long reservationId = accepted.get("reservation").get("reservationId").asLong();

        JsonNode detailA = conversationDetail(buyerA, conversationA);
        assertThat(detailA.get("reservation").get("reservationId").asLong()).isEqualTo(reservationId);
        assertThat(detailA.get("reservation").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(detailA.get("reservation").get("offerId").asLong()).isEqualTo(accepted.get("offerId").asLong());
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
    void 다른_회원의_결제는_구매_가능_수량까지만_허용되고_예약자의_합의_가격_결제는_예약을_끝낸다() throws Exception {
        prepare();
        JsonNode accepted = reserveByOffer(buyerA, conversationA, 3);
        long offerId = accepted.get("offerId").asLong();
        long reservationId = accepted.get("reservation").get("reservationId").asLong();
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

        JsonNode order = placeOrder(buyerA, productId, 3, offerId);
        long orderId = order.get("orderId").asLong();
        assertThat(order.get("priceSource").asText()).isEqualTo("AGREED");
        assertThat(order.get("paidAmount").asLong()).isEqualTo(24_000);
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
    void 예약_중_등록_가격_결제는_409_AGREED_OFFER_REQUIRED이고_합의_가격으로_결제한_뒤에는_등록_가격으로_결제할_수_있다() throws Exception {
        prepare();
        JsonNode accepted = reserveByOffer(buyerA, conversationA, 3);
        long offerId = accepted.get("offerId").asLong();
        long reservationId = accepted.get("reservation").get("reservationId").asLong();
        charge(buyerA, 50_000);

        JsonNode required = json(postJson("/api/orders", buyerA, orderBody(productId, 1, null)).andExpect(status().isConflict()));
        assertThat(required.get("code").asText()).isEqualTo("AGREED_OFFER_REQUIRED");
        assertThat(orderCount(productId)).isZero();
        assertThat(reservationStatus(reservationId)).isEqualTo("ACTIVE");
        assertThat(balanceOf(buyerA)).isEqualTo(50_000);

        placeOrder(buyerA, productId, 3, offerId);
        assertThat(reservationStatus(reservationId)).isEqualTo("FULFILLED");
        JsonNode listed = placeOrder(buyerA, productId, 1, null);
        assertThat(listed.get("priceSource").asText()).isEqualTo("LISTED");
        assertThat(listed.get("unitPrice").asLong()).isEqualTo(10_000);
        // 예약이 끝났으므로 다른 회원의 구매 가능 수량은 남은 수량 1 과 같다.
        assertThat(availableQuantityOfProduct(buyerB)).isEqualTo(1);
    }

    @Test
    void 판매자와_구매_희망자는_해제할_수_있고_제3자는_403_끝난_예약은_409_없는_예약은_404이다() throws Exception {
        prepare();
        long first = reserveByOffer(buyerA, conversationA, 3).get("reservation").get("reservationId").asLong();
        JsonNode byOutsider = json(release(member("outsider"), first).andExpect(status().isForbidden()));
        assertThat(byOutsider.get("code").asText()).isEqualTo("NOT_CONVERSATION_MEMBER");
        assertThat(reservationStatus(first)).isEqualTo("ACTIVE");

        JsonNode bySeller = json(release(seller, first).andExpect(status().isOk()));
        assertThat(bySeller.get("status").asText()).isEqualTo("RELEASED");
        assertThat(bySeller.get("releasedBy").asText()).isEqualTo("SELLER");
        assertThat(bySeller.get("endedAt").isNull()).isFalse();
        JsonNode again = json(release(seller, first).andExpect(status().isConflict()));
        assertThat(again.get("code").asText()).isEqualTo("RESERVATION_NOT_ACTIVE");

        // 합의가 해제되면 같은 구매 희망자가 새로 제안할 수 있고, 판매자가 수락하면 다시 예약된다.
        long second = reserveByOffer(buyerA, conversationA, 3).get("reservation").get("reservationId").asLong();
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
        long reservationId = reserveByOffer(buyerA, conversationA, 2).get("reservation").get("reservationId").asLong();

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
    void 합의_가격_주문을_취소하면_같은_제안의_새_예약_행이_활성으로_생기고_다시_20분이_지나면_자동_해제된다() throws Exception {
        prepare();
        JsonNode accepted = reserveByOffer(buyerA, conversationA, 3);
        long offerId = accepted.get("offerId").asLong();
        long firstReservation = accepted.get("reservation").get("reservationId").asLong();
        charge(buyerA, 50_000);
        clock.advance(Duration.ofMinutes(5));
        long orderId = placeOrder(buyerA, productId, 3, offerId).get("orderId").asLong();

        clock.advance(Duration.ofMinutes(3));
        Instant cancelledAt = clock.instant();
        cancel(buyerA, orderId);

        // 결제로 끝난 행은 그대로 두고 같은 제안 ID 의 새 행을 만든다.
        assertThat(reservationCount("")).isEqualTo(2);
        assertThat(reservationStatus(firstReservation)).isEqualTo("FULFILLED");
        assertThat(jdbcTemplate.queryForObject("SELECT FULFILLED_ORDER_ID FROM RESERVATION WHERE RESERVATION_ID = ?",
                Long.class, firstReservation)).isEqualTo(orderId);
        JsonNode detail = conversationDetail(buyerA, conversationA);
        JsonNode renewed = detail.get("reservation");
        long secondReservation = renewed.get("reservationId").asLong();
        assertThat(secondReservation).isNotEqualTo(firstReservation);
        assertThat(renewed.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(renewed.get("offerId").asLong()).isEqualTo(offerId);
        assertThat(renewed.get("quantity").asLong()).isEqualTo(3);
        assertThat(renewed.get("conversationId").asLong()).isEqualTo(conversationA);
        assertThat(Instant.parse(renewed.get("reservedAt").asText())).isEqualTo(cancelledAt);
        assertThat(Instant.parse(renewed.get("expiresAt").asText())).isEqualTo(cancelledAt.plus(Duration.ofMinutes(20)));
        assertThat(detail.get("offers").get(0).get("agreementStatus").asText()).isEqualTo("RESERVED");
        // 취소로 돌아온 수량 3 이 다시 예약되므로 다른 회원의 구매 가능 수량은 취소 전과 같은 2 다.
        assertThat(remainingQuantity(productId)).isEqualTo(5);
        assertThat(availableQuantityOfProduct(buyerB)).isEqualTo(2);

        clock.advance(Duration.ofMinutes(19).plusSeconds(59));
        assertThat(reservationService.expireReservations(clock.instant())).isZero();
        clock.advance(Duration.ofSeconds(1));
        assertThat(reservationService.expireReservations(clock.instant())).isEqualTo(1);
        assertThat(reservationStatus(secondReservation)).isEqualTo("EXPIRED");
        assertThat(reservationStatus(firstReservation)).isEqualTo("FULFILLED");
    }

    @Test
    void 환불된_합의_가격_주문은_새_예약_행을_만들지_않는다() throws Exception {
        prepare();
        JsonNode accepted = reserveByOffer(buyerA, conversationA, 2);
        long offerId = accepted.get("offerId").asLong();
        charge(buyerA, 50_000);
        long orderId = placeOrder(buyerA, productId, 2, offerId).get("orderId").asLong();

        ship(seller, orderId);
        Instant deliveredAt = clock.instant().plus(Duration.ofMinutes(2));
        clock.setInstant(deliveredAt);
        assertThat(shipmentService.completeDueDeliveries(deliveredAt)).isEqualTo(1);
        postJson("/api/orders/" + orderId + "/refund-request", buyerA,
                Map.of("reasonCode", "DESCRIPTION_MISMATCH", "detail", "설명과 달리 화면에 흠집이 있습니다."))
                .andExpect(status().isCreated());
        postJson("/api/orders/" + orderId + "/refund-request/approve", seller, null).andExpect(status().isOk());
        assertThat(tradeStatus(orderId)).isEqualTo("REFUNDED");

        assertThat(reservationCount("")).isEqualTo(1);
        assertThat(reservationCount(" AND STATUS = 'FULFILLED'")).isEqualTo(1);
        assertThat(conversationDetail(buyerA, conversationA).get("offers").get(0).get("agreementStatus").asText())
                .isEqualTo("EXPIRED");
    }

    @Test
    void 예약이_만료되거나_해제된_뒤_그_제안으로_결제하면_409_OFFER_RESERVATION_ENDED이다() throws Exception {
        prepare();
        long expiredOffer = reserveByOffer(buyerA, conversationA, 2).get("offerId").asLong();
        charge(buyerA, 50_000);
        clock.advance(Duration.ofMinutes(20));
        JsonNode expired = json(postJson("/api/orders", buyerA, orderBody(productId, 2, expiredOffer))
                .andExpect(status().isConflict()));
        assertThat(expired.get("code").asText()).isEqualTo("OFFER_RESERVATION_ENDED");

        JsonNode acceptedB = reserveByOffer(buyerB, conversationB, 2);
        release(seller, acceptedB.get("reservation").get("reservationId").asLong()).andExpect(status().isOk());
        charge(buyerB, 50_000);
        JsonNode released = json(postJson("/api/orders", buyerB, orderBody(productId, 2, acceptedB.get("offerId").asLong()))
                .andExpect(status().isConflict()));
        assertThat(released.get("code").asText()).isEqualTo("OFFER_RESERVATION_ENDED");

        assertThat(orderCount(productId)).isZero();
        assertThat(balanceOf(buyerA)).isEqualTo(50_000);
        assertThat(balanceOf(buyerB)).isEqualTo(50_000);
    }

    @Test
    void 수락으로_예약이_걸리면_상품의_다른_대화_세션이_구매_가능_수량이_담긴_CONVERSATION_STATE를_받는다() throws Exception {
        prepare();
        long offerA = propose(buyerA, conversationA, OFFER_AMOUNT, 3);
        WsTestClient.Connection buyerBWs = WsTestClient.connect(port, buyerB, "?conversationId=" + conversationB);
        long deadline = System.currentTimeMillis() + 5_000;
        while (!registry.sessionsOf(conversationB).containsKey(buyerB.id()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        try {
            acceptRequest(offerA).andExpect(status().isOk());
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
