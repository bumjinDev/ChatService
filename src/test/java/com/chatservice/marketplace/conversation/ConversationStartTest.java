package com.chatservice.marketplace.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;

/** F-005 상품별 채팅 시작·이어가기 검증. */
class ConversationStartTest extends IntegrationTestSupport {

	@Autowired
	private IConversationService conversationService;

	@Autowired
	private ConversationRepository conversationRepository;

	@Test
	void F005_같은_회원이_같은_상품에_두_번_시작하면_201_다음_200_이고_같은_대화다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 50_000);

		String first = mockMvc.perform(post("/api/products/" + product.getProductId() + "/conversations")
				.cookie(authCookie(buyer)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.myRole").value("BUYER"))
				.andExpect(jsonPath("$.writable").value(true))
				.andExpect(jsonPath("$.readOnlyReason").doesNotExist())
				.andExpect(jsonPath("$.product.productId").value(product.getProductId()))
				.andExpect(jsonPath("$.product.status").value("ON_SALE"))
				.andExpect(jsonPath("$.buyer.nickname").value("구매자"))
				.andExpect(jsonPath("$.seller.nickname").value("판매자"))
				.andExpect(jsonPath("$.offers.length()").value(0))
				.andExpect(jsonPath("$.order").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		Number firstId = JsonPath.read(first, "$.conversationId");

		mockMvc.perform(post("/api/products/" + product.getProductId() + "/conversations")
				.cookie(authCookie(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.conversationId").value(firstId.longValue()));

		assertThat(conversationRepository.count()).isEqualTo(1);
		Conversation saved = conversationRepository.findAll().get(0);
		assertThat(saved.getBuyerId()).isEqualTo(buyer);
		assertThat(saved.getSellerId()).isEqualTo(seller);
		assertThat(saved.isCreatedBySystem()).isFalse();
	}

	@Test
	void F005_AC02_서로_다른_구매_희망자는_각각_다른_대화를_만든다() {
		String seller = member("it_seller", "판매자");
		String buyerA = member("it_buyer_a", "구매자A");
		String buyerB = member("it_buyer_b", "구매자B");
		Product product = product(seller, 50_000);

		ConversationStartResult a = conversationService.start(buyerA, product.getProductId());
		ConversationStartResult b = conversationService.start(buyerB, product.getProductId());

		assertThat(a.created()).isTrue();
		assertThat(b.created()).isTrue();
		assertThat(a.conversation().conversationId()).isNotEqualTo(b.conversation().conversationId());
		assertThat(conversationRepository.findByProductIdOrderByConversationIdAsc(product.getProductId()))
				.extracting(Conversation::getBuyerId).containsExactly(buyerA, buyerB);
	}

	@Test
	void F005_본인_상품에_시작하면_403() throws Exception {
		String seller = member("it_seller", "판매자");
		Product product = product(seller, 50_000);

		mockMvc.perform(post("/api/products/" + product.getProductId() + "/conversations")
				.cookie(authCookie(seller)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SELF_TRADE_NOT_ALLOWED"));
		assertThat(conversationRepository.count()).isZero();
	}

	@Test
	void F005_기존_대화_없이_판매_종료_상품에_시작하면_409() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = markSold(product(seller, 50_000));

		mockMvc.perform(post("/api/products/" + product.getProductId() + "/conversations")
				.cookie(authCookie(buyer)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_ON_SALE"));
		assertThat(conversationRepository.count()).isZero();
	}

	@Test
	void 기존_대화가_있으면_판매_종료_상품이라도_200으로_돌려준다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 50_000);
		ConversationStartResult created = conversationService.start(buyer, product.getProductId());
		markSold(product);

		ConversationStartResult again = conversationService.start(buyer, product.getProductId());

		assertThat(again.created()).isFalse();
		assertThat(again.conversation().conversationId()).isEqualTo(created.conversation().conversationId());
		// 주문 없이 판매 종료된 상품: 이 구매 희망자의 주문이 아니므로 읽기 전용이다.
		assertThat(again.conversation().writable()).isFalse();
		assertThat(again.conversation().readOnlyReason()).isEqualTo(ReadOnlyReason.PRODUCT_SOLD_TO_OTHER);
	}

	@Test
	void 없는_상품이면_404() {
		String buyer = member("it_buyer", "구매자");
		assertThatThrownBy(() -> conversationService.start(buyer, 999_999_999L))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
	}

	@Test
	void 인증_없이_시작하면_401() throws Exception {
		String seller = member("it_seller", "판매자");
		Product product = product(seller, 50_000);
		mockMvc.perform(post("/api/products/" + product.getProductId() + "/conversations"))
				.andExpect(status().isUnauthorized());
	}
}
