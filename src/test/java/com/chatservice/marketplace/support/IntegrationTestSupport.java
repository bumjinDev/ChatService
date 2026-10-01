package com.chatservice.marketplace.support;

import java.security.Key;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.chatservice.auth.filter.util.JWTUtil;
import com.chatservice.marketplace.product.Category;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductRepository;
import com.chatservice.redis.handler.RedisHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

/**
 * 통합 테스트 공통 설정. 실제 Oracle 테스트 스키마와 Redis 를 사용한다.
 * 모든 테스트가 같은 설정을 써서 Spring 컨텍스트를 한 번만 띄운다.
 * 테스트마다 마켓플레이스 테이블과 테스트 회원(ID 가 it_ 로 시작)을 지우고 시각을 기준 시각으로 되돌린다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfraConfig.class)
public abstract class IntegrationTestSupport {

	/** 외래 키 순서(자식 → 부모)로 지울 테이블 */
	private static final List<String> TABLES_TO_CLEAN = List.of(
			"BALANCE_TRANSACTION", "CHAT_MESSAGE", "REFUND_REQUEST", "SHIPMENT", "PURCHASE_ORDER",
			"PRICE_OFFER", "CONVERSATION", "WALLET", "PRODUCT");

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	@Autowired
	protected MutableClock clock;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JWTUtil jwtUtil;

	@Autowired
	protected ProductRepository productRepository;

	@Autowired
	private RedisHandler redisHandler;

	@LocalServerPort
	protected int port;

	@BeforeEach
	void resetState() {
		// 커넥션 풀이 auto-commit=false 이므로 테스트 데이터 변경은 트랜잭션 안에서 커밋한다.
		inTransaction(() -> {
			for (String table : TABLES_TO_CLEAN) {
				jdbcTemplate.update("DELETE FROM " + table);
			}
			jdbcTemplate.update("DELETE FROM MEMBERTBL WHERE ID LIKE 'it\\_%' ESCAPE '\\'");
		});
		clock.set(TestTimes.BASE);
	}

	/** 주어진 작업을 트랜잭션 하나로 실행하고 커밋한다. */
	protected void inTransaction(Runnable work) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
	}

	/** MEMBERTBL 에 테스트 회원을 만든다. ID 는 it_ 로 시작해야 정리 대상이 된다. */
	protected String member(String id, String nickname) {
		if (!id.startsWith("it_")) {
			throw new IllegalArgumentException("테스트 회원 ID 는 it_ 로 시작해야 한다: " + id);
		}
		inTransaction(() -> jdbcTemplate.update(
				"INSERT INTO MEMBERTBL (ID, PW, NICKNAME, TEL, EMAIL, JOINDATE) VALUES (?, 'x', ?, '010-0000-0000', 'it@test.local', SYSDATE)",
				id, nickname));
		return id;
	}

	/** LoginFilter 와 같은 방식으로 JWT 를 발급해 Redis 에 저장하고 Authorization 쿠키 값을 돌려준다. */
	protected String token(String memberId, String nickname) {
		Key key = jwtUtil.generateSigningKey();
		String jwt = jwtUtil.generateToken(nickname, memberId, List.of("ROLE_USER"), key);
		redisHandler.getValueOperations().set(jwt, jwtUtil.encodeKeyToBase64(key), 30, TimeUnit.MINUTES);
		return jwt;
	}

	protected Cookie authCookie(String memberId) {
		String nickname = jdbcTemplate.queryForObject("SELECT NICKNAME FROM MEMBERTBL WHERE ID = ?", String.class,
				memberId);
		return new Cookie("Authorization", token(memberId, nickname));
	}

	/** 판매 중 상품을 저장소로 직접 만든다. */
	protected Product product(String sellerId, long price) {
		return productRepository.saveAndFlush(
				Product.register(sellerId, "상품" + price, "상품 설명", Category.ETC, price, clock.instant()));
	}

	/** 상품을 판매 종료 상태로 바꾼다(주문 없이 상태만 바꾸는 준비 작업). */
	protected Product markSold(Product product) {
		product.markSold(clock.instant());
		return productRepository.saveAndFlush(product);
	}

	protected String json(Object body) throws Exception {
		return objectMapper.writeValueAsString(body);
	}
}
