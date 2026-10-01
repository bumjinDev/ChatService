package com.chatservice.marketplace.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.chatservice.marketplace.product.Category;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.support.IntegrationTestSupport;

/**
 * 실행 구성 검증: 레거시 패키지 스캔 제외, 메인 화면, API 인증 실패 응답 형식, Instant 와 Oracle TIMESTAMP 의 매핑.
 */
class FoundationIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private Clock appClock;

	@Test
	void 사용을_중단한_레거시_패키지는_빈으로_등록되지_않는다() {
		for (String name : context.getBeanDefinitionNames()) {
			Object bean = context.getBean(name);
			String pkg = bean.getClass().getName();
			assertThat(pkg).doesNotMatch(
					"com\\.chatservice\\.(createroom|joinroom|concurrency|scheduler|roomlist|web|websocketcore|redis\\.controller|redis\\.service)\\..*");
		}
		assertThat(context.containsBean("webController")).isFalse();
		assertThat(context.containsBean("mainPageController")).isTrue();
	}

	@Test
	void 서비스는_테스트에서_주입한_Clock_을_쓴다() {
		assertThat(appClock.instant()).isEqualTo(clock.instant());
	}

	@Test
	void 메인_화면은_집계_없이_index_를_그린다() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/WEB-INF/views/index.jsp"))
				.andExpect(model().attribute("userName", "none"))
				.andExpect(model().attributeDoesNotExist("totalRoom", "totalUser"));
	}

	@Test
	void 사용을_중단한_방_목록_경로는_404() throws Exception {
		String me = member("it_found1", "기초1");
		mockMvc.perform(get("/rooms").cookie(authCookie(me))).andExpect(status().isNotFound());
	}

	@Test
	void 인증_없는_API_요청은_JSON_401() throws Exception {
		mockMvc.perform(get("/api/wallet"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void Instant_는_UTC_값으로_TIMESTAMP_에_저장되고_그대로_읽힌다() {
		String seller = member("it_found2", "기초2");
		Instant at = Instant.parse("2026-09-28T01:02:03.123456Z");
		Product saved = productRepository.saveAndFlush(
				Product.register(seller, "시각 확인", "설명", Category.ETC, 1000L, at));

		String stored = jdbcTemplate.queryForObject(
				"SELECT TO_CHAR(CREATED_AT, 'YYYY-MM-DD HH24:MI:SS.FF6') FROM PRODUCT WHERE PRODUCT_ID = ?",
				String.class, saved.getProductId());
		assertThat(stored).isEqualTo("2026-09-28 01:02:03.123456");
		assertThat(productRepository.findById(saved.getProductId()).orElseThrow().getCreatedAt()).isEqualTo(at);
	}
}
