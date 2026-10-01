package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.support.OrderTestSupport;
import com.chatservice.marketplace.wallet.TransactionType;

/** F-018 판매자 환불 응답·무응답 자동 환불 검증(환불 정책 CH-001). */
class RefundDecisionTest extends OrderTestSupport {

	@Autowired
	private IRefundService refundService;

	@Autowired
	private ITradeCompletionService completionService;

	@Autowired
	private RefundRequestRepository refundRequestRepository;

	/** 배송 완료 후 1시간에 환불을 접수한 보류 주문. 반환 후 Clock 은 접수 시각에 있다. */
	private PurchaseOrder heldOrder(String seller, String buyer, long price) {
		PurchaseOrder order = deliveredOrder(seller, buyer, price);
		clock.advance(Duration.ofHours(1));
		refundService.requestRefund(buyer, order.getOrderId(),
				new RefundRequestCreateRequest(RefundReasonCode.DESCRIPTION_MISMATCH, "설명과 다름", null));
		return orderRepository.findById(order.getOrderId()).orElseThrow();
	}

	private RefundRequest refundOf(PurchaseOrder order) {
		return refundRequestRepository.findFirstByOrderIdOrderByRefundRequestIdAsc(order.getOrderId()).orElseThrow();
	}

	@Test
	void F018_AC01_접수_47시간에_판매자가_동의하면_REFUNDED_구매자_전액_반환_판매대금_없음() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = heldOrder(seller, buyer, 30_000);
		clock.set(refundOf(order).getRequestedAt().plus(Duration.ofHours(47)));

		mockMvc.perform(post("/api/orders/" + order.getOrderId() + "/refund-request/approve").cookie(authCookie(seller)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tradeStatus").value("REFUNDED"))
				.andExpect(jsonPath("$.refundRequest.decision").value("APPROVED"))
				.andExpect(jsonPath("$.refundRequest.decidedAt").value(clock.instant().toString()))
				.andExpect(jsonPath("$.finalizedAt").value(clock.instant().toString()));

		assertThat(balance(buyer)).isEqualTo(30_000L);
		assertThat(transactions(buyer, TransactionType.REFUND)).singleElement().satisfies(tx -> {
			assertThat(tx.getAmount()).isEqualTo(30_000L);
			assertThat(tx.getOrderId()).isEqualTo(order.getOrderId());
		});
		assertThat(balance(seller)).isZero();
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).isEmpty();
	}

	@Test
	void F018_AC02_접수_47시간에_판매자가_거절하면_REFUND_REJECTED_정상_완료와_판매대금_지급_구매자_반환_없음() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = heldOrder(seller, buyer, 30_000);
		clock.set(refundOf(order).getRequestedAt().plus(Duration.ofHours(47)));

		mockMvc.perform(post("/api/orders/" + order.getOrderId() + "/refund-request/reject").cookie(authCookie(seller)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tradeStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.completionCause").value("REFUND_REJECTED"))
				.andExpect(jsonPath("$.refundRequest.decision").value("REJECTED"))
				.andExpect(jsonPath("$.myBalanceTransactions[0].type").value("SALE_PAYOUT"));

		assertThat(balance(seller)).isEqualTo(30_000L);
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).hasSize(1);
		assertThat(transactions(buyer, TransactionType.REFUND)).isEmpty();
		assertThat(balance(buyer)).isZero();
	}

	@Test
	void F018_AC03_순차_정확히_48시간에_자동_실행이_된_뒤_판매자가_늦게_거절하면_409_REFUND_ALREADY_DECIDED() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = heldOrder(seller, buyer, 30_000);
		Instant deadline = refundOf(order).getResponseDeadlineAt();

		assertThat(refundService.autoApproveExpired(deadline.minusSeconds(1))).isZero();
		assertThat(refundService.autoApproveExpired(deadline)).isEqualTo(1);
		clock.set(deadline.plusSeconds(10));

		assertThat(errorOf(() -> refundService.decide(seller, order.getOrderId(), false)))
				.isEqualTo(ErrorCode.REFUND_ALREADY_DECIDED);

		PurchaseOrder refunded = orderRepository.findById(order.getOrderId()).orElseThrow();
		assertThat(refunded.getTradeStatus()).isEqualTo(TradeStatus.REFUNDED);
		assertThat(refunded.getFinalizedAt()).isEqualTo(deadline);
		RefundRequest refund = refundOf(order);
		assertThat(refund.getDecision()).isEqualTo(RefundDecision.AUTO_APPROVED);
		assertThat(refund.getDecidedAt()).isEqualTo(deadline);
		assertThat(transactions(buyer, TransactionType.REFUND)).hasSize(1);
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).isEmpty();
	}

	@Test
	void 정확히_48시간에는_자동_실행_전이라도_판매자가_응답할_수_없다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = heldOrder(seller, buyer, 30_000);
		clock.set(refundOf(order).getResponseDeadlineAt());

		assertThat(errorOf(() -> refundService.decide(seller, order.getOrderId(), true)))
				.isEqualTo(ErrorCode.REFUND_RESPONSE_DEADLINE_PASSED);
		assertThat(refundOf(order).getDecision()).isEqualTo(RefundDecision.PENDING);
		assertThat(transactions(buyer, TransactionType.REFUND)).isEmpty();
	}

	@Test
	void F018_AC04_환불_완료_뒤_자동_처리를_다시_실행해도_추가_반환이_없다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = heldOrder(seller, buyer, 30_000);
		Instant deadline = refundOf(order).getResponseDeadlineAt();
		refundService.autoApproveExpired(deadline);

		assertThat(refundService.autoApproveExpired(deadline.plus(Duration.ofHours(1)))).isZero();
		assertThat(refundService.autoApproveExpired(deadline.plus(Duration.ofDays(3)))).isZero();

		assertThat(transactions(buyer, TransactionType.REFUND)).hasSize(1);
		assertThat(balance(buyer)).isEqualTo(30_000L);

		// 판매자 동의로 환불된 주문도 자동 처리 대상이 아니다.
		String buyer2 = member("it_buyer2", "구매자2");
		PurchaseOrder order2 = heldOrder(seller, buyer2, 20_000);
		refundService.decide(seller, order2.getOrderId(), true);
		assertThat(refundService.autoApproveExpired(clock.instant().plus(Duration.ofDays(5)))).isZero();
		assertThat(transactions(buyer2, TransactionType.REFUND)).hasSize(1);
	}

	@Test
	void 판단을_반복하면_409_판매자가_아니면_403_요청이_없으면_404() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = heldOrder(seller, buyer, 30_000);

		mockMvc.perform(post("/api/orders/" + order.getOrderId() + "/refund-request/approve").cookie(authCookie(buyer)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_SELLER"));

		refundService.decide(seller, order.getOrderId(), true);
		assertThat(errorOf(() -> refundService.decide(seller, order.getOrderId(), false)))
				.isEqualTo(ErrorCode.REFUND_ALREADY_DECIDED);
		assertThat(transactions(buyer, TransactionType.REFUND)).hasSize(1);
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).isEmpty();

		PurchaseOrder noRefund = deliveredOrder(seller, member("it_buyer3", "구매자3"), 10_000);
		mockMvc.perform(post("/api/orders/" + noRefund.getOrderId() + "/refund-request/reject")
				.cookie(authCookie(seller)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("REFUND_REQUEST_NOT_FOUND"));
	}

	@Test
	void 보류가_끝난_뒤에는_정상_수령이나_자동_완료로_다시_지급되지_않는다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = heldOrder(seller, buyer, 30_000);
		refundService.decide(seller, order.getOrderId(), true);

		assertThat(errorOf(() -> completionService.confirmReceipt(buyer, order.getOrderId())))
				.isEqualTo(ErrorCode.TRADE_ALREADY_FINALIZED);
		assertThat(completionService.completeExpiredInspections(clock.instant().plus(Duration.ofDays(5)))).isZero();
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).isEmpty();
	}

	@Test
	void 스케줄러는_무응답_자동_환불도_실행한다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = heldOrder(seller, buyer, 30_000);
		clock.set(refundOf(order).getResponseDeadlineAt());

		newScheduler().run();

		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getTradeStatus())
				.isEqualTo(TradeStatus.REFUNDED);
	}
}
