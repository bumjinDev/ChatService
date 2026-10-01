package com.chatservice.marketplace.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;

/** F-002 상품 목록·상세 조회 검증. */
class ProductQueryTest extends IntegrationTestSupport {

	@Autowired
	private IProductService productService;

	private Product saveProduct(String sellerId, String name, Category category, long price) {
		Product product = productRepository.saveAndFlush(
				Product.register(sellerId, name, name + " 설명", category, price, clock.instant()));
		clock.advance(Duration.ofMinutes(1));
		return product;
	}

	@Test
	void F002_AC01_목록에는_판매_중_상품만_최신순으로_나오고_카테고리_필터가_동작한다() {
		String seller = member("it_seller", "판매자");
		Product book = saveProduct(seller, "책", Category.BOOKS_HOBBY, 1000);
		Product phone = saveProduct(seller, "휴대폰", Category.ELECTRONICS, 200000);
		Product sold = saveProduct(seller, "노트북", Category.ELECTRONICS, 500000);
		markSold(sold);

		assertThat(productService.listOnSale(null))
				.extracting(ProductSummaryResponse::productId)
				.containsExactly(phone.getProductId(), book.getProductId());
		assertThat(productService.listOnSale(Category.ELECTRONICS))
				.extracting(ProductSummaryResponse::productId)
				.containsExactly(phone.getProductId());
		assertThat(productService.listOnSale(Category.LIVING)).isEmpty();
		assertThat(productService.listOnSale(null).get(0).sellerNickname()).isEqualTo("판매자");
	}

	@Test
	void 목록은_인증_없이_조회되고_설명을_포함하지_않는다() throws Exception {
		String seller = member("it_seller", "판매자");
		saveProduct(seller, "책", Category.BOOKS_HOBBY, 1000);

		mockMvc.perform(get("/api/products").param("category", "BOOKS_HOBBY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("책"))
				.andExpect(jsonPath("$[0].sellerNickname").value("판매자"))
				.andExpect(jsonPath("$[0].description").doesNotExist());
	}

	@Test
	void 목록의_카테고리_값이_다섯_값이_아니면_400() throws Exception {
		mockMvc.perform(get("/api/products").param("category", "FOOD"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.category").exists());
	}

	@Test
	void F001_AC01_등록한_상품은_목록과_상세에서_조회된다() throws Exception {
		String seller = member("it_seller", "판매자");
		String created = mockMvc.perform(post("/api/products").cookie(authCookie(seller))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"의자\",\"description\":\"원목\",\"category\":\"LIVING\",\"price\":30000}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Number productId = JsonPath.read(created, "$.productId");

		mockMvc.perform(get("/api/products"))
				.andExpect(jsonPath("$[0].productId").value(productId.longValue()));
		mockMvc.perform(get("/api/products/" + productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value("원목"))
				.andExpect(jsonPath("$.status").value("ON_SALE"));
	}

	@Test
	void 상세는_인증_없이_판매_중_상품의_모든_필드를_돌려준다() throws Exception {
		String seller = member("it_seller", "판매자");
		Product book = saveProduct(seller, "책", Category.BOOKS_HOBBY, 1000);

		mockMvc.perform(get("/api/products/" + book.getProductId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productId").value(book.getProductId()))
				.andExpect(jsonPath("$.name").value("책"))
				.andExpect(jsonPath("$.description").value("책 설명"))
				.andExpect(jsonPath("$.category").value("BOOKS_HOBBY"))
				.andExpect(jsonPath("$.price").value(1000))
				.andExpect(jsonPath("$.status").value("ON_SALE"))
				.andExpect(jsonPath("$.sellerNickname").value("판매자"))
				.andExpect(jsonPath("$.createdAt").exists());
	}

	@Test
	void F002_AC02_판매_종료_상품의_공개_상세는_404() throws Exception {
		String seller = member("it_seller", "판매자");
		Product sold = saveProduct(seller, "노트북", Category.ELECTRONICS, 500000);
		markSold(sold);

		mockMvc.perform(get("/api/products/" + sold.getProductId()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void 없는_상품의_상세는_404() throws Exception {
		mockMvc.perform(get("/api/products/999999999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}
}
