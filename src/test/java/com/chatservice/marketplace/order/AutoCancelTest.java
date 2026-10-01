package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.chatservice.marketplace.order.scheduler.TradeScheduler;
import com.chatservice.marketplace.support.OrderTestSupport;
import com.chatservice.marketplace.support.TestTimes;
import com.chatservice.marketplace.wallet.TransactionType;

/** F-013 미발송 자동 취소 검증. 스케줄러를 기다리지 않고 서비스 메서드를 직접 호출한다. */
class AutoCancelTest extends OrderTestSupport {

	@Autowired
	private IOrderCancellationService cancellationService;

	@Autowired
	private IShipmentService shipmentService;

	@Autowired
	private ApplicationContext context;

	@Test
	void F013_AC01_월요일_확정_미발송_주문은_화요일_0시_KST_에_자동_취소되고_전액_반환된다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = purchase(buyer, product(seller, 30_000)); // 2026-09-28(월) 확정

		// 기한 직전(다음 월요일 23:59 KST)에는 대상이 아니다.
		assertThat(cancellationService.cancelExpiredUnshipped(TestTimes.kst(2026, 10, 5, 23, 59))).isZero();
		assertThat(orderRepository.findById(order.orderId()).orElseThrow().getTradeStatus())
				.isEqualTo(TradeStatus.IN_PROGRESS);

		var deadline = TestTimes.kst(2026, 10, 6, 0, 0);
		assertThat(cancellationService.cancelExpiredUnshipped(deadline)).isEqualTo(1);

		PurchaseOrder cancelled = orderRepository.findById(order.orderId()).orElseThrow();
		assertThat(cancelled.getTradeStatus()).isEqualTo(TradeStatus.CANCELLED);
		assertThat(cancelled.getCancelledBy()).isEqualTo(CancelledBy.SYSTEM);
		assertThat(cancelled.getCancelReason()).isEqualTo("SHIPMENT_DEADLINE_EXPIRED");
		assertThat(cancelled.getFinalizedAt()).isEqualTo(deadline);
		assertThat(balance(buyer)).isEqualTo(30_000L);
		assertThat(transactions(buyer, TransactionType.CANCEL_REFUND)).singleElement().satisfies(tx -> {
			assertThat(tx.getAmount()).isEqualTo(30_000L);
			assertThat(tx.getOrderId()).isEqualTo(order.orderId());
		});
	}

	@Test
	void F013_AC02_배송_중_주문은_자동_취소_대상이_아니다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = purchase(buyer, product(seller, 30_000));
		shipmentService.registerShipment(seller, order.orderId(), new ShipmentRegisterRequest("우체국", "1", null));

		assertThat(cancellationService.cancelExpiredUnshipped(TestTimes.kst(2026, 10, 20, 0, 0))).isZero();
		assertThat(orderRepository.findById(order.orderId()).orElseThrow().getTradeStatus())
				.isEqualTo(TradeStatus.IN_PROGRESS);
		assertThat(transactions(buyer, TransactionType.CANCEL_REFUND)).isEmpty();
	}

	@Test
	void 순차로_다시_실행하거나_이미_취소된_주문은_다시_처리하지_않는다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse auto = purchase(buyer, product(seller, 30_000));
		String buyer2 = member("it_buyer2", "구매자2");
		OrderDetailResponse manual = purchase(buyer2, product(seller, 20_000));
		cancellationService.cancelByParty(buyer2, manual.orderId(), new OrderCancelRequest("변심"));

		var after = TestTimes.kst(2026, 10, 7, 0, 0);
		assertThat(cancellationService.cancelExpiredUnshipped(after)).isEqualTo(1);
		assertThat(cancellationService.cancelExpiredUnshipped(after)).isZero();

		assertThat(transactions(buyer, TransactionType.CANCEL_REFUND)).hasSize(1);
		assertThat(transactions(buyer2, TransactionType.CANCEL_REFUND)).hasSize(1);
		assertThat(orderRepository.findById(manual.orderId()).orElseThrow().getCancelledBy())
				.isEqualTo(CancelledBy.BUYER);
		assertThat(orderRepository.findById(auto.orderId()).orElseThrow().getCancelledBy())
				.isEqualTo(CancelledBy.SYSTEM);
	}

	@Test
	void 테스트_설정에서는_스케줄러_빈이_만들어지지_않고_스케줄러는_현재_시각으로_자동_취소를_호출한다() {
		assertThat(context.getBeansOfType(TradeScheduler.class)).isEmpty();

		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = purchase(buyer, product(seller, 30_000));
		clock.set(TestTimes.kst(2026, 10, 6, 0, 0));

		new TradeScheduler(cancellationService, clock).run();

		assertThat(orderRepository.findById(order.orderId()).orElseThrow().getTradeStatus())
				.isEqualTo(TradeStatus.CANCELLED);
	}
}
