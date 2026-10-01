package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.support.OrderTestSupport;
import com.chatservice.marketplace.wallet.TransactionType;

/** F-016 상품 확인 기간 만료 자동 완료 검증. 자동 처리 메서드를 직접 호출한다. */
class AutoCompleteTest extends OrderTestSupport {

	@Autowired
	private ITradeCompletionService completionService;

	@Autowired
	private RefundRequestRepository refundRequestRepository;

	@Test
	void F016_AC01_환불_요청_없이_정확히_48시간이_되면_자동_완료되고_지급은_1건() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		Instant deadline = order.getDeliveredAt().plus(Duration.ofHours(48));

		assertThat(completionService.completeExpiredInspections(deadline.minusSeconds(1))).isZero();
		assertThat(completionService.completeExpiredInspections(deadline)).isEqualTo(1);

		PurchaseOrder completed = orderRepository.findById(order.getOrderId()).orElseThrow();
		assertThat(completed.getTradeStatus()).isEqualTo(TradeStatus.COMPLETED);
		assertThat(completed.getCompletionCause()).isEqualTo(CompletionCause.AUTO_EXPIRED);
		assertThat(completed.getFinalizedAt()).isEqualTo(deadline);
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).singleElement()
				.satisfies(tx -> assertThat(tx.getAmount()).isEqualTo(30_000L));
		assertThat(balance(seller)).isEqualTo(30_000L);

		// 순차로 다시 실행해도 추가 지급이 없다.
		assertThat(completionService.completeExpiredInspections(deadline.plus(Duration.ofHours(1)))).isZero();
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).hasSize(1);
	}

	@Test
	void F016_AC02_47시간_59분에_환불이_접수된_뒤_48시간_자동_처리가_실행되면_보류가_유지되고_지급되지_않는다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		Instant requestedAt = order.getDeliveredAt().plus(Duration.ofHours(47)).plus(Duration.ofMinutes(59));
		// 환불 접수 상태를 저장소로 만든다(환불 요청 기능은 F-017 에서 검증).
		inTransaction(() -> {
			PurchaseOrder entity = orderRepository.findById(order.getOrderId()).orElseThrow();
			entity.hold();
			refundRequestRepository.save(RefundRequest.submit(entity.getOrderId(), buyer,
					RefundReasonCode.DESCRIPTION_MISMATCH, "설명과 다름", requestedAt,
					requestedAt.plus(Duration.ofHours(48)), null));
		});

		assertThat(completionService.completeExpiredInspections(order.getDeliveredAt().plus(Duration.ofHours(48))))
				.isZero();
		// 자동 실행이 늦어져도 마찬가지다.
		assertThat(completionService.completeExpiredInspections(order.getDeliveredAt().plus(Duration.ofHours(60))))
				.isZero();

		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getTradeStatus())
				.isEqualTo(TradeStatus.ON_HOLD);
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).isEmpty();
	}

	@Test
	void 수령_확인으로_이미_완료된_주문은_다시_완료하지_않는다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		completionService.confirmReceipt(buyer, order.getOrderId());

		assertThat(completionService.completeExpiredInspections(order.getDeliveredAt().plus(Duration.ofHours(48))))
				.isZero();
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).hasSize(1);
		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getCompletionCause())
				.isEqualTo(CompletionCause.BUYER_CONFIRMED);
	}

	@Test
	void 스케줄러는_확인_기간_만료_자동_완료도_실행한다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		clock.set(order.getInspectionDeadlineAt());

		newScheduler().run();

		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getTradeStatus())
				.isEqualTo(TradeStatus.COMPLETED);
	}
}
