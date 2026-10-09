package com.chatservice.marketplace.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.chatservice.auth.filter.util.JWTUtil;
import com.chatservice.redis.handler.RedisHandler;
import com.chatservice.user.dao.MemberEntityRepository;
import com.chatservice.user.model.MembersEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 통합 테스트 공통 기반.
 *
 * - 실제 보안 체인·컨트롤러·서비스·JPA·DB 를 거친다. 인증은 테스트 회원마다 실제 JWT 를 만들어 Redis 에 서명키를 저장한다.
 * - 데이터는 회원 ID 접두어 "itest" 로 만든 행만 지운다. 테스트 전용 스키마를 쓰더라도 다른 데이터는 건드리지 않는다.
 * - 시각은 MutableClock 으로 고정한다. 기본 시작 시각은 2026-10-05(월) 10:00 KST 이다.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({IntegrationTestConfig.class, H2IntegrationDataSourceConfig.class})
public abstract class IntegrationTestSupport {

    protected static final String MEMBER_PREFIX = "itest";

    /** 2026-10-05 월요일 10:00 KST */
    protected static final Instant MONDAY_10_KST = Instant.parse("2026-10-05T01:00:00Z");

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private static final List<String> CLEANUP_SQL = List.of(
            "DELETE FROM BALANCE_TRANSACTION WHERE MEMBER_ID LIKE 'itest%'",
            "DELETE FROM CHAT_MESSAGE WHERE CONVERSATION_ID IN "
                    + "(SELECT CONVERSATION_ID FROM CONVERSATION WHERE BUYER_ID LIKE 'itest%' OR SELLER_ID LIKE 'itest%')",
            "DELETE FROM REFUND_REQUEST WHERE ORDER_ID IN "
                    + "(SELECT ORDER_ID FROM PURCHASE_ORDER WHERE BUYER_ID LIKE 'itest%' OR SELLER_ID LIKE 'itest%')",
            "DELETE FROM SHIPMENT WHERE ORDER_ID IN "
                    + "(SELECT ORDER_ID FROM PURCHASE_ORDER WHERE BUYER_ID LIKE 'itest%' OR SELLER_ID LIKE 'itest%')",
            "DELETE FROM PURCHASE_ORDER WHERE BUYER_ID LIKE 'itest%' OR SELLER_ID LIKE 'itest%'",
            "DELETE FROM PRICE_OFFER WHERE CONVERSATION_ID IN "
                    + "(SELECT CONVERSATION_ID FROM CONVERSATION WHERE BUYER_ID LIKE 'itest%' OR SELLER_ID LIKE 'itest%')",
            "DELETE FROM CONVERSATION WHERE BUYER_ID LIKE 'itest%' OR SELLER_ID LIKE 'itest%'",
            "DELETE FROM PRODUCT WHERE SELLER_ID LIKE 'itest%'",
            "DELETE FROM WALLET WHERE MEMBER_ID LIKE 'itest%'",
            "DELETE FROM MEMBERTBL WHERE ID LIKE 'itest%'");

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    private MemberEntityRepository memberEntityRepository;
    @Autowired
    private JWTUtil jwtUtil;
    @Autowired
    private RedisHandler redisHandler;

    @LocalServerPort
    protected int port;

    private final List<String> redisKeys = new ArrayList<>();

    @BeforeEach
    void prepareBase() {
        deleteTestData();
        clock.setInstant(MONDAY_10_KST);
    }

    @AfterEach
    void cleanUpBase() {
        deleteTestData();
        for (String key : redisKeys) {
            redisHandler.getValueOperations().getOperations().delete(key);
        }
        redisKeys.clear();
    }

    private void deleteTestData() {
        for (String sql : CLEANUP_SQL) {
            jdbcTemplate.update(sql);
        }
    }

    /** MEMBERTBL 에 회원을 만들고, 로그인과 같은 방식으로 JWT 와 서명키를 Redis 에 저장한다. */
    protected TestMember member(String label) {
        int seq = SEQUENCE.incrementAndGet();
        String id = MEMBER_PREFIX + seq + label;
        String nickname = MEMBER_PREFIX + "-" + label + seq;
        memberEntityRepository.save(MembersEntity.builder()
                .id(id).pw("unused").nickName(nickname).tel("010-0000-0000").email(id + "@example.com")
                .joinDate(Date.valueOf(LocalDate.of(2026, 1, 1)))
                .build());

        Key key = jwtUtil.generateSigningKey();
        String token = jwtUtil.generateToken(nickname, id, List.of("ROLE_USER"), key);
        redisHandler.getValueOperations().set(token, jwtUtil.encodeKeyToBase64(key), 30, TimeUnit.MINUTES);
        redisHandler.getValueOperations().set(id, token, 30, TimeUnit.MINUTES);
        redisKeys.add(token);
        redisKeys.add(id);
        return new TestMember(id, nickname, token);
    }

    // ---------------------------------------------------------------- HTTP helpers

    protected ResultActions postJson(String path, TestMember member, Object body) throws Exception {
        MockHttpServletRequestBuilder request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(body instanceof String raw ? raw : objectMapper.writeValueAsString(body));
        }
        if (member != null) {
            request.cookie(member.cookie());
        }
        return mockMvc.perform(request);
    }

    protected ResultActions getJson(String path, TestMember member) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (member != null) {
            request.cookie(member.cookie());
        }
        return mockMvc.perform(request);
    }

    protected JsonNode json(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- domain helpers

    protected long registerProduct(TestMember seller, long price, long quantity) throws Exception {
        return registerProduct(seller, "ELECTRONICS", price, quantity);
    }

    protected long registerProduct(TestMember seller, String category, long price, long quantity) throws Exception {
        java.util.Map<String, Object> body = java.util.Map.of(
                "name", "테스트 상품", "description", "상품 설명", "category", category,
                "price", price, "quantity", quantity);
        JsonNode created = json(postJson("/api/products", seller, body)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()));
        return created.get("productId").asLong();
    }

    protected void charge(TestMember member, long amount) throws Exception {
        postJson("/api/wallet/charges", member, java.util.Map.of("amount", amount))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
    }

    /** 대화를 시작(또는 이어가기)하고 conversationId 를 돌려준다. */
    protected long startConversation(TestMember buyer, long productId) throws Exception {
        return json(postJson("/api/products/" + productId + "/conversations", buyer, null)).get("conversationId").asLong();
    }

    protected long sendMessage(TestMember sender, long conversationId, String content) throws Exception {
        JsonNode sent = json(postJson("/api/conversations/" + conversationId + "/messages", sender,
                java.util.Map.of("content", content))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()));
        return sent.get("messageId").asLong();
    }

    protected long propose(TestMember buyer, long conversationId, long amount) throws Exception {
        JsonNode offer = json(postJson("/api/conversations/" + conversationId + "/offers", buyer,
                java.util.Map.of("amount", amount))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()));
        return offer.get("offerId").asLong();
    }

    protected void respond(TestMember seller, long offerId, boolean accept) throws Exception {
        postJson("/api/offers/" + offerId + (accept ? "/accept" : "/reject"), seller, null)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    protected String offerStatus(long offerId) {
        return jdbcTemplate.queryForObject("SELECT STATUS FROM PRICE_OFFER WHERE OFFER_ID = ?", String.class, offerId);
    }

    protected java.util.Map<String, Object> orderBody(long productId, Object quantity, Long offerId) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        body.put("recipientName", "홍길동");
        body.put("shippingAddress", "서울시 중구 세종대로 1");
        if (offerId != null) {
            body.put("offerId", offerId);
        }
        return body;
    }

    /** 결제가 성공해야 하는 경우의 결제 요청. 응답(주문 상세)을 돌려준다. */
    protected JsonNode placeOrder(TestMember buyer, long productId, long quantity, Long offerId) throws Exception {
        return json(postJson("/api/orders", buyer, orderBody(productId, quantity, offerId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()));
    }

    protected JsonNode ship(TestMember seller, long orderId) throws Exception {
        return json(postJson("/api/orders/" + orderId + "/shipment", seller,
                java.util.Map.of("carrierName", "테스트택배", "trackingNumber", "1234-5678"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()));
    }

    protected JsonNode cancel(TestMember member, long orderId) throws Exception {
        return json(postJson("/api/orders/" + orderId + "/cancel", member, java.util.Map.of("reason", "단순 변심"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()));
    }

    // ---------------------------------------------------------------- DB helpers

    protected long remainingQuantity(long productId) {
        return jdbcTemplate.queryForObject("SELECT REMAINING_QUANTITY FROM PRODUCT WHERE PRODUCT_ID = ?", Long.class, productId);
    }

    protected String productStatus(long productId) {
        return jdbcTemplate.queryForObject("SELECT STATUS FROM PRODUCT WHERE PRODUCT_ID = ?", String.class, productId);
    }

    protected String tradeStatus(long orderId) {
        return jdbcTemplate.queryForObject("SELECT TRADE_STATUS FROM PURCHASE_ORDER WHERE ORDER_ID = ?", String.class, orderId);
    }

    protected String shippingStatus(long orderId) {
        return jdbcTemplate.queryForObject("SELECT SHIPPING_STATUS FROM PURCHASE_ORDER WHERE ORDER_ID = ?", String.class, orderId);
    }

    protected long orderCount(long productId) {
        return count("SELECT COUNT(*) FROM PURCHASE_ORDER WHERE PRODUCT_ID = ?", productId);
    }

    /** DB 에 저장된 잔액. 지갑 행이 없으면 0. */
    protected long balanceOf(TestMember member) {
        List<Long> balances = jdbcTemplate.queryForList(
                "SELECT BALANCE FROM WALLET WHERE MEMBER_ID = ?", Long.class, member.id());
        return balances.isEmpty() ? 0 : balances.get(0);
    }

    protected long transactionCount(TestMember member, String type) {
        return count("SELECT COUNT(*) FROM BALANCE_TRANSACTION WHERE MEMBER_ID = ? AND TYPE = ?", member.id(), type);
    }

    protected long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
