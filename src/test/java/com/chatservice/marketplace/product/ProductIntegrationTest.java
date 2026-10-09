package com.chatservice.marketplace.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.fasterxml.jackson.databind.JsonNode;

/** F-001 상품 등록, F-002 상품 목록·상세 조회 (설계 9장 F-001-AC-01~04, F-002-AC-01·02). */
class ProductIntegrationTest extends IntegrationTestSupport {

    private Map<String, Object> validBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "중고 노트북");
        body.put("description", "배터리 상태 양호");
        body.put("category", "ELECTRONICS");
        body.put("price", 50_000);
        body.put("quantity", 1);
        return body;
    }

    private long productCount() {
        return count("SELECT COUNT(*) FROM PRODUCT WHERE SELLER_ID LIKE 'itest%'");
    }

    @Test
    void F001_AC01_유효한_다섯_필수_정보로_등록하면_판매_중으로_목록과_상세에서_조회된다() throws Exception {
        TestMember seller = member("seller");

        JsonNode created = json(postJson("/api/products", seller, validBody()).andExpect(status().isCreated()));
        long productId = created.get("productId").asLong();
        assertThat(created.get("status").asText()).isEqualTo("ON_SALE");
        assertThat(created.get("sellerNickname").asText()).isEqualTo(seller.nickname());

        JsonNode list = json(getJson("/api/products", null).andExpect(status().isOk()));
        JsonNode item = findById(list, productId);
        assertThat(item).isNotNull();
        assertThat(item.has("description")).isFalse();
        assertThat(item.get("remainingQuantity").asLong()).isEqualTo(1);

        JsonNode detail = json(getJson("/api/products/" + productId, null).andExpect(status().isOk()));
        assertThat(detail.get("description").asText()).isEqualTo("배터리 상태 양호");
        assertThat(detail.get("price").asLong()).isEqualTo(50_000);
        assertThat(detail.get("initialQuantity").asLong()).isEqualTo(1);
    }

    @Test
    void F001_AC02_가격_카테고리_상품명_오류는_400이고_상품이_만들어지지_않는다() throws Exception {
        TestMember seller = member("seller");
        Object[][] cases = {
                {"price", 0}, {"price", -1}, {"price", 1.5}, {"price", null},
                {"price", 1_000_000_000_000_000L},
                {"category", "FOOD"}, {"category", null},
                {"name", null}, {"name", "  "}, {"name", "가".repeat(101)},
                {"description", null}, {"description", ""}
        };
        for (Object[] invalid : cases) {
            Map<String, Object> body = validBody();
            body.put((String) invalid[0], invalid[1]);
            JsonNode error = json(postJson("/api/products", seller, body).andExpect(status().isBadRequest()));
            assertThat(error.get("code").asText()).as("case %s=%s", invalid[0], invalid[1]).isEqualTo("VALIDATION_ERROR");
            assertThat(error.get("fieldErrors").has((String) invalid[0])).as("field %s", invalid[0]).isTrue();
        }
        assertThat(productCount()).isZero();
    }

    @Test
    void F001_AC03_판매_수량_1과_5는_최초_남은_수량으로_저장되고_판매_중이다() throws Exception {
        TestMember seller = member("seller");
        for (long quantity : new long[] {1, 5}) {
            Map<String, Object> body = validBody();
            body.put("quantity", quantity);
            JsonNode created = json(postJson("/api/products", seller, body).andExpect(status().isCreated()));
            assertThat(created.get("initialQuantity").asLong()).isEqualTo(quantity);
            assertThat(created.get("remainingQuantity").asLong()).isEqualTo(quantity);
            assertThat(created.get("status").asText()).isEqualTo("ON_SALE");
        }
    }

    @Test
    void F001_AC04_판매_수량_누락_0_음수_소수_저장범위초과는_400이고_상품이_없다() throws Exception {
        TestMember seller = member("seller");
        Object[] invalidQuantities = {null, 0, -1, 1.5, 10_000_000_000L};
        for (Object quantity : invalidQuantities) {
            Map<String, Object> body = validBody();
            body.put("quantity", quantity);
            JsonNode error = json(postJson("/api/products", seller, body).andExpect(status().isBadRequest()));
            assertThat(error.get("fieldErrors").has("quantity")).as("quantity=%s", quantity).isTrue();
        }
        assertThat(productCount()).isZero();
    }

    @Test
    void 저장_범위_최대값의_가격과_수량은_등록된다() throws Exception {
        TestMember seller = member("seller");
        Map<String, Object> body = validBody();
        body.put("price", 999_999_999_999_999L);
        body.put("quantity", 9_999_999_999L);
        JsonNode created = json(postJson("/api/products", seller, body).andExpect(status().isCreated()));
        assertThat(created.get("price").asLong()).isEqualTo(999_999_999_999_999L);
        assertThat(created.get("remainingQuantity").asLong()).isEqualTo(9_999_999_999L);
    }

    @Test
    void 인증_없이_등록하면_401이다() throws Exception {
        postJson("/api/products", null, validBody()).andExpect(status().isUnauthorized());
        assertThat(productCount()).isZero();
    }

    @Test
    void F002_AC01_목록은_판매_중_상품만_보여주고_카테고리로_거른다() throws Exception {
        TestMember seller = member("seller");
        long book = registerProduct(seller, "BOOKS_HOBBY", 10_000, 1);
        long laptop = registerProduct(seller, "ELECTRONICS", 20_000, 2);
        long sold = registerProduct(seller, "ELECTRONICS", 30_000, 1);
        // 결제 기능과 무관하게 판매 종료 상태를 만든다. 실제 결제로 판매 종료되는 경우는 주문 테스트에서 확인한다.
        jdbcTemplate.update("UPDATE PRODUCT SET REMAINING_QUANTITY = 0, STATUS = 'SOLD' WHERE PRODUCT_ID = ?", sold);

        JsonNode all = json(getJson("/api/products", null).andExpect(status().isOk()));
        assertThat(findById(all, book)).isNotNull();
        assertThat(findById(all, laptop)).isNotNull();
        assertThat(findById(all, sold)).isNull();

        JsonNode electronics = json(getJson("/api/products?category=ELECTRONICS", null).andExpect(status().isOk()));
        assertThat(findById(electronics, laptop)).isNotNull();
        assertThat(findById(electronics, book)).isNull();
        assertThat(findById(electronics, sold)).isNull();
    }

    @Test
    void F002_목록_카테고리_값이_틀리면_400이다() throws Exception {
        JsonNode error = json(getJson("/api/products?category=FOOD", null).andExpect(status().isBadRequest()));
        assertThat(error.get("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void F002_AC02_판매_종료_상품과_없는_상품은_공개_상세가_404이다() throws Exception {
        TestMember seller = member("seller");
        long sold = registerProduct(seller, 30_000, 1);
        jdbcTemplate.update("UPDATE PRODUCT SET REMAINING_QUANTITY = 0, STATUS = 'SOLD' WHERE PRODUCT_ID = ?", sold);

        JsonNode error = json(getJson("/api/products/" + sold, null).andExpect(status().isNotFound()));
        assertThat(error.get("code").asText()).isEqualTo("PRODUCT_NOT_FOUND");
        getJson("/api/products/999999999", null).andExpect(status().isNotFound());
    }

    @Test
    void 등록_시각은_UTC_벽시계로_저장되고_같은_값으로_다시_읽힌다() throws Exception {
        // 통합 테스트 JVM 의 기본 시간대는 Asia/Seoul 이다(build.gradle integrationTest).
        Instant registeredAt = Instant.parse("2026-10-05T01:02:03.456789Z");
        clock.setInstant(registeredAt);
        long productId = registerProduct(member("seller"), 10_000, 1);

        java.sql.Timestamp raw = jdbcTemplate.queryForObject(
                "SELECT CREATED_AT FROM PRODUCT WHERE PRODUCT_ID = ?", java.sql.Timestamp.class, productId);
        assertThat(raw.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-10-05T01:02:03.456789"));

        JsonNode detail = json(getJson("/api/products/" + productId, null));
        assertThat(Instant.parse(detail.get("createdAt").asText())).isEqualTo(registeredAt);
    }

    private static JsonNode findById(JsonNode array, long productId) {
        for (JsonNode node : array) {
            if (node.get("productId").asLong() == productId) {
                return node;
            }
        }
        return null;
    }
}
