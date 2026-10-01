package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.support.OrderTestSupport;
import com.chatservice.marketplace.wallet.TransactionType;

/** F-015 정상 수령 확인·판매대금 지급 검증. */
class ConfirmReceiptTest extends OrderTestSupport {

	@Autowired
	private ITradeCompletionService completionService;

	@Autowired
	private IShipmentService shipmentService;

	@Autowired
	private RefundRequestRepository refundRequestRepository;

	@Test
	void F015_AC01_배송_완료_47시간_뒤_구매자가_확인하면_COMPLETED_와_판매대금_1건() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		clock.set(order.getDeliveredAt().plus(Duration.ofHours(47)));

		mockMvc.perform(post("/api/orders/" + order.getOrderId() + "/confirm-receipt").cookie(authCookie(buyer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tradeStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.completionCause").value("BUYER_CONFIRMED"))
				.andExpect(jsonPath("$.finalizedAt").value(clock.instant().toString()));

		assertThat(balance(seller)).isEqualTo(30_000L);
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).singleElement().satisfies(tx -> {
			assertThat(tx.getAmount()).isEqualTo(30_000L);
			assertThat(tx.getOrderId()).isEqualTo(order.getOrderId());
		});
		assertThat(transactions(buyer, TransactionType.REFUND)).isEmpty();
	}

	@Test
	void F015_AC02_보류_상태에서_확인하면_409_ORDER_ON_HOLD_이고_지급되지_않는다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		// 환불 요청 접수 상태를 저장소로 만든다(환불 요청 기능은 F-017 에서 검증).
		inTransaction(() -> {
			PurchaseOrder entity = orderRepository.findById(order.getOrderId()).orElseThrow();
			entity.hold();
			refundRequestRepository.save(RefundRequest.submit(entity.getOrderId(), buyer,
					RefundReasonCode.DESCRIPTION_MISMATCH, "설명과 다름", clock.instant(),
					clock.instant().plus(Duration.ofHours(48)), null));
		});

		assertThat(errorOf(() -> completionService.confirmReceipt(buyer, order.getOrderId())))
				.isEqualTo(ErrorCode.ORDER_ON_HOLD);
		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getTradeStatus())
				.isEqualTo(TradeStatus.ON_HOLD);
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).isEmpty();
	}

	@Test
	void F015_AC03_정확히_48시간에_확인하면_409_INSPECTION_PERIOD_ENDED() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		clock.set(order.getDeliveredAt().plus(Duration.ofHours(48)));

		assertThat(errorOf(() -> completionService.confirmReceipt(buyer, order.getOrderId())))
				.isEqualTo(ErrorCode.INSPECTION_PERIOD_ENDED);
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).isEmpty();
	}

	@Test
	void F015_반복_완료한_뒤_다시_확인하면_409이고_지급은_1건() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		completionService.confirmReceipt(buyer, order.getOrderId());

		assertThat(errorOf(() -> completionService.confirmReceipt(buyer, order.getOrderId())))
				.isEqualTo(ErrorCode.TRADE_ALREADY_FINALIZED);
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).hasSize(1);
		assertThat(balance(seller)).isEqualTo(30_000L);
	}

	@Test
	void 배송_완료_전이면_409_ORDER_NOT_DELIVERED_구매자가_아니면_403() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse waiting = purchase(buyer, product(seller, 30_000));
		assertThat(errorOf(() -> completionService.confirmReceipt(buyer, waiting.orderId())))
				.isEqualTo(ErrorCode.ORDER_NOT_DELIVERED);
		shipmentService.registerShipment(seller, waiting.orderId(), new ShipmentRegisterRequest("우체국", "1", null));
		assertThat(errorOf(() -> completionService.confirmReceipt(buyer, waiting.orderId())))
				.isEqualTo(ErrorCode.ORDER_NOT_DELIVERED);

		mockMvc.perform(post("/api/orders/" + waiting.orderId() + "/confirm-receipt").cookie(authCookie(seller)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_BUYER"));
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).isEmpty();
	}
}
