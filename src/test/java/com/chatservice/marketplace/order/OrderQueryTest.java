package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.support.OrderTestSupport;

/** F-019 주문·배송·결제·최종 금전 결과 조회 검증. */
class OrderQueryTest extends OrderTestSupport {

	@Autowired
	private IOrderQueryService queryService;

	@Autowired
	private IRefundService refundService;

	@Test
	void F019_AC01_결제_성공_뒤_구매_목록과_상세에서_주문_결제_금액_PURCHASE_내역이_보인다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		Product product = product(seller, 30_000);
		charge(buyer, 50_000);
		// 결제 응답을 받지 못했다고 보고 저장된 결과를 조회로 확인한다.
		orderService.placeOrder(buyer, listedRequest(product));
		Long orderId = orderRepository.findAll().get(0).getOrderId();

		mockMvc.perform(get("/api/orders/purchases").cookie(authCookie(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].orderId").value(orderId))
				.andExpect(jsonPath("$[0].product.productId").value(product.getProductId()))
				.andExpect(jsonPath("$[0].product.name").value(product.getName()))
				.andExpect(jsonPath("$[0].paidAmount").value(30000))
				.andExpect(jsonPath("$[0].priceSource").value("LISTED"))
				.andExpect(jsonPath("$[0].shippingStatus").value("WAITING_SHIPMENT"))
				.andExpect(jsonPath("$[0].tradeStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$[0].confirmedAt").exists())
				.andExpect(jsonPath("$[0].recipientName").doesNotExist());

		mockMvc.perform(get("/api/orders/" + orderId).cookie(authCookie(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orderId").value(orderId))
				.andExpect(jsonPath("$.product.description").value("상품 설명"))
				.andExpect(jsonPath("$.buyerNickname").value("구매자"))
				.andExpect(jsonPath("$.sellerNickname").value("판매자"))
				.andExpect(jsonPath("$.myRole").value("BUYER"))
				.andExpect(jsonPath("$.paidAmount").value(30000))
				.andExpect(jsonPath("$.recipientName").value("홍길동"))
				.andExpect(jsonPath("$.shippingAddress").value("서울시 중구 세종대로 1"))
				.andExpect(jsonPath("$.shipment").doesNotExist())
				.andExpect(jsonPath("$.cancellation").doesNotExist())
				.andExpect(jsonPath("$.refundRequest").doesNotExist())
				.andExpect(jsonPath("$.myBalanceTransactions.length()").value(1))
				.andExpect(jsonPath("$.myBalanceTransactions[0].type").value("PURCHASE"))
				.andExpect(jsonPath("$.myBalanceTransactions[0].amount").value(-30000))
				.andExpect(jsonPath("$.myBalanceTransactions[0].balanceAfter").value(20000));
	}

	@Test
	void F019_AC02_환불_완료_뒤_당사자가_조회하면_REFUNDED_와_구매자의_REFUND_내역이_보인다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		clock.advance(Duration.ofHours(1));
		refundService.requestRefund(buyer, order.getOrderId(),
				new RefundRequestCreateRequest(RefundReasonCode.DESCRIPTION_MISMATCH, "다름", null));
		refundService.decide(seller, order.getOrderId(), true);

		mockMvc.perform(get("/api/orders/" + order.getOrderId()).cookie(authCookie(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tradeStatus").value("REFUNDED"))
				.andExpect(jsonPath("$.shippingStatus").value("DELIVERED"))
				.andExpect(jsonPath("$.shipment.carrierName").value("우체국"))
				.andExpect(jsonPath("$.deliveredAt").exists())
				.andExpect(jsonPath("$.inspectionDeadlineAt").exists())
				.andExpect(jsonPath("$.refundRequest.decision").value("APPROVED"))
				.andExpect(jsonPath("$.finalizedAt").exists())
				.andExpect(jsonPath("$.myBalanceTransactions.length()").value(2))
				.andExpect(jsonPath("$.myBalanceTransactions[1].type").value("REFUND"))
				.andExpect(jsonPath("$.myBalanceTransactions[1].amount").value(30000));

		// 판매자는 같은 주문의 결과를 보되, 본인 잔액 변동은 없다.
		mockMvc.perform(get("/api/orders/" + order.getOrderId()).cookie(authCookie(seller)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.myRole").value("SELLER"))
				.andExpect(jsonPath("$.tradeStatus").value("REFUNDED"))
				.andExpect(jsonPath("$.myBalanceTransactions.length()").value(0));
	}

	@Test
	void F019_AC03_제3자가_상세를_조회하면_403이고_수령인과_주소가_노출되지_않는다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String stranger = member("it_stranger", "제3자");
		OrderDetailResponse order = purchase(buyer, product(seller, 30_000));

		String body = mockMvc.perform(get("/api/orders/" + order.orderId()).cookie(authCookie(stranger)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_TRADE_PARTY"))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("홍길동", "세종대로", "recipientName", "shippingAddress");

		mockMvc.perform(get("/api/orders/purchases").cookie(authCookie(stranger)))
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/orders/sales").cookie(authCookie(stranger)))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 판매_목록은_판매자의_주문만_구매_확정_시각_내림차순으로_보여준다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		String otherSeller = member("it_other_seller", "다른판매자");
		OrderDetailResponse first = purchase(buyer, product(seller, 10_000));
		clock.advance(Duration.ofMinutes(1));
		OrderDetailResponse second = purchase(buyer, product(seller, 20_000));
		purchase(buyer, product(otherSeller, 5_000));

		assertThat(queryService.listSales(seller)).extracting(OrderSummaryResponse::orderId)
				.containsExactly(second.orderId(), first.orderId());
		assertThat(queryService.listPurchases(buyer)).hasSize(3);
		assertThat(queryService.listPurchases(seller)).isEmpty();
	}

	@Test
	void 없는_주문은_404_인증이_없으면_401() throws Exception {
		String buyer = member("it_buyer", "구매자");
		mockMvc.perform(get("/api/orders/999999999").cookie(authCookie(buyer)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
		mockMvc.perform(get("/api/orders/purchases")).andExpect(status().isUnauthorized());
	}
}
