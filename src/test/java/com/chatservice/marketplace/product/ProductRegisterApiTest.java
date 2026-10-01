package com.chatservice.marketplace.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestTimes;

/** F-001 상품 등록 API 검증. */
class ProductRegisterApiTest extends IntegrationTestSupport {

	@Test
	void F001_AC01_유효한_네_필수_정보로_등록하면_201과_ON_SALE() throws Exception {
		String seller = member("it_seller", "판매자");
		String body = """
				{"name":"자전거","description":"거의 새것","category":"LIVING","price":50000}
				""";

		mockMvc.perform(post("/api/products").cookie(authCookie(seller))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.productId").isNumber())
				.andExpect(jsonPath("$.name").value("자전거"))
				.andExpect(jsonPath("$.description").value("거의 새것"))
				.andExpect(jsonPath("$.category").value("LIVING"))
				.andExpect(jsonPath("$.price").value(50000))
				.andExpect(jsonPath("$.status").value("ON_SALE"))
				.andExpect(jsonPath("$.sellerNickname").value("판매자"))
				.andExpect(jsonPath("$.createdAt").value(TestTimes.BASE.toString()));

		assertThat(productRepository.findAll()).singleElement().satisfies(p -> {
			assertThat(p.getSellerId()).isEqualTo(seller);
			assertThat(p.getStatus()).isEqualTo(ProductStatus.ON_SALE);
		});
	}

	@Test
	void 같은_내용으로_다시_등록하면_별도의_상품이_만들어진다() throws Exception {
		String seller = member("it_seller", "판매자");
		String body = """
				{"name":"책","description":"설명","category":"BOOKS_HOBBY","price":1000}
				""";
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/api/products").cookie(authCookie(seller))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isCreated());
		}
		assertThat(productRepository.count()).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"name\":\"a\",\"description\":\"d\",\"category\":\"ETC\",\"price\":0}",
			"{\"name\":\"a\",\"description\":\"d\",\"category\":\"ETC\",\"price\":-100}",
			"{\"name\":\"a\",\"description\":\"d\",\"category\":\"ETC\",\"price\":1000.5}",
			"{\"name\":\"a\",\"description\":\"d\",\"category\":\"FOOD\",\"price\":1000}",
			"{\"description\":\"d\",\"category\":\"ETC\",\"price\":1000}",
			"{\"name\":\"   \",\"description\":\"d\",\"category\":\"ETC\",\"price\":1000}",
			"{\"name\":\"a\",\"category\":\"ETC\",\"price\":1000}",
			"{\"name\":\"a\",\"description\":\"d\",\"price\":1000}"
	})
	void F001_AC02_잘못된_입력은_400_VALIDATION_ERROR_이고_상품이_만들어지지_않는다(String body) throws Exception {
		String seller = member("it_seller", "판매자");

		mockMvc.perform(post("/api/products").cookie(authCookie(seller))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.fieldErrors").isMap());

		assertThat(productRepository.count()).isZero();
	}

	@Test
	void 가격_오류는_fieldErrors_에_price_로_표시된다() throws Exception {
		String seller = member("it_seller", "판매자");
		mockMvc.perform(post("/api/products").cookie(authCookie(seller))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"a\",\"description\":\"d\",\"category\":\"ETC\",\"price\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.price").exists());
	}

	@Test
	void 상품명이_100자를_넘으면_400() throws Exception {
		String seller = member("it_seller", "판매자");
		String name = "가".repeat(101);
		mockMvc.perform(post("/api/products").cookie(authCookie(seller))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"description\":\"d\",\"category\":\"ETC\",\"price\":10}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists());
	}

	@Test
	void 인증_없이_등록하면_401() throws Exception {
		mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"a\",\"description\":\"d\",\"category\":\"ETC\",\"price\":10}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		assertThat(productRepository.count()).isZero();
	}
}
