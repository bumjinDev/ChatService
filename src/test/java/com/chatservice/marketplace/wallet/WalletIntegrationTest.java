package com.chatservice.marketplace.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * F-003 테스트 잔액 충전, F-004 잔액·변동 내역 조회(설계 9장 F-003-AC-02, F-004-AC-02).
 * F-004-AC-01(충전·구매·반환 내역)은 주문 기능이 필요하므로 ShipmentAndCancellationIntegrationTest 에서 확인한다.
 * F-003-AC-01(같은 요청 재시도 시 한 번만 반영)은 후속 과제이며 여기서 검증하지 않는다.
 */
class WalletIntegrationTest extends IntegrationTestSupport {

    @Test
    void F003_AC02_서로_다른_10000원_충전_두_건은_잔액_20000원과_충전_내역_두_건이_된다() throws Exception {
        TestMember member = member("buyer");

        JsonNode first = json(postJson("/api/wallet/charges", member, Map.of("amount", 10_000)).andExpect(status().isCreated()));
        JsonNode second = json(postJson("/api/wallet/charges", member, Map.of("amount", 10_000)).andExpect(status().isCreated()));

        assertThat(first.get("balance").asLong()).isEqualTo(10_000);
        assertThat(first.get("transaction").get("type").asText()).isEqualTo("CHARGE");
        assertThat(first.get("transaction").get("balanceAfter").asLong()).isEqualTo(10_000);
        assertThat(second.get("balance").asLong()).isEqualTo(20_000);
        assertThat(second.get("transaction").get("balanceAfter").asLong()).isEqualTo(20_000);
        assertThat(balanceOf(member)).isEqualTo(20_000);

        JsonNode transactions = json(getJson("/api/wallet/transactions", member).andExpect(status().isOk()));
        assertThat(transactions).hasSize(2);
        // 최신순
        assertThat(transactions.get(0).get("balanceAfter").asLong()).isEqualTo(20_000);
        assertThat(transactions.get(1).get("balanceAfter").asLong()).isEqualTo(10_000);
        assertThat(transactions.get(0).get("amount").asLong()).isEqualTo(10_000);
        assertThat(transactions.get(0).get("orderId").isNull()).isTrue();
    }

    @Test
    void 기본_구현은_같은_requestId_로_다시_보내도_별도_충전으로_반영한다() throws Exception {
        // 1.4절: 충전은 호출마다 새 요청으로 처리한다. 재시도 식별은 후속 과제이므로 현재 동작만 기록한다.
        TestMember member = member("buyer");
        Map<String, Object> body = Map.of("amount", 1_000, "requestId", "same-request");
        postJson("/api/wallet/charges", member, body).andExpect(status().isCreated());
        postJson("/api/wallet/charges", member, body).andExpect(status().isCreated());
        assertThat(balanceOf(member)).isEqualTo(2_000);
        assertThat(count("SELECT COUNT(*) FROM BALANCE_TRANSACTION WHERE MEMBER_ID = ? AND REQUEST_ID = 'same-request'",
                member.id())).isEqualTo(2);
    }

    @Test
    void F003_잘못된_충전_금액은_400이고_잔액과_내역이_바뀌지_않는다() throws Exception {
        TestMember member = member("buyer");
        charge(member, 5_000);
        Object[] invalidAmounts = {0, -1, 1.5, null, 1_000_000_000_000_000L, "abc"};
        for (Object amount : invalidAmounts) {
            Map<String, Object> body = new HashMap<>();
            body.put("amount", amount);
            JsonNode error = json(postJson("/api/wallet/charges", member, body).andExpect(status().isBadRequest()));
            assertThat(error.get("code").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(error.get("fieldErrors").has("amount")).as("amount=%s", amount).isTrue();
        }
        assertThat(balanceOf(member)).isEqualTo(5_000);
        assertThat(transactionCount(member, "CHARGE")).isEqualTo(1);
    }

    @Test
    void 충전_후_잔액이_저장_범위를_넘으면_400이고_잔액이_그대로다() throws Exception {
        TestMember member = member("buyer");
        charge(member, 999_999_999_999_999L);
        JsonNode error = json(postJson("/api/wallet/charges", member, Map.of("amount", 1)).andExpect(status().isBadRequest()));
        assertThat(error.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(balanceOf(member)).isEqualTo(999_999_999_999_999L);
        assertThat(transactionCount(member, "CHARGE")).isEqualTo(1);
    }

    @Test
    void F004_AC02_잔액_조회는_경로에_회원을_받지_않고_본인_것만_돌려준다() throws Exception {
        TestMember owner = member("owner");
        TestMember other = member("other");
        charge(owner, 7_000);

        JsonNode mine = json(getJson("/api/wallet", other).andExpect(status().isOk()));
        assertThat(mine.get("memberId").asText()).isEqualTo(other.id());
        assertThat(mine.get("balance").asLong()).isZero();
        JsonNode otherTransactions = json(getJson("/api/wallet/transactions", other).andExpect(status().isOk()));
        assertThat(otherTransactions).isEmpty();

        // 다른 회원 ID 를 경로에 붙여도 해당 경로가 없다.
        getJson("/api/wallet/" + owner.id(), other).andExpect(status().isNotFound());
        assertThat(balanceOf(owner)).isEqualTo(7_000);
    }

    @Test
    void 최초_잔액_조회는_잔액_0인_지갑을_만든다() throws Exception {
        TestMember member = member("fresh");
        assertThat(count("SELECT COUNT(*) FROM WALLET WHERE MEMBER_ID = ?", member.id())).isZero();
        getJson("/api/wallet", member).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM WALLET WHERE MEMBER_ID = ? AND BALANCE = 0", member.id())).isEqualTo(1);
    }

    @Test
    void 인증_없이_충전하면_401이다() throws Exception {
        postJson("/api/wallet/charges", null, Map.of("amount", 1_000)).andExpect(status().isUnauthorized());
    }
}
