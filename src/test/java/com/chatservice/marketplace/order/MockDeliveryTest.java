package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.support.OrderTestSupport;
import com.chatservice.marketplace.wallet.TransactionType;

/** F-014 모의 배송 완료 검증. 자동 처리 메서드를 직접 호출한다. */
class MockDeliveryTest extends OrderTestSupport {

	@Autowired
	private IShipmentService shipmentService;

	@Autowired
	private IOrderCancellationService cancellationService;

	@Autowired
	private ShipmentRepository shipmentRepository;

	private OrderDetailResponse shippedOrder(String seller, String buyer) {
		OrderDetailResponse order = purchase(buyer, product(seller, 30_000));
		clock.advance(Duration.ofHours(1));
		return shipmentService.registerShipment(seller, order.orderId(),
				new ShipmentRegisterRequest("우체국", "1", null));
	}

	@Test
	void F014_AC01_Clock_을_deliveryDueAt_으로_옮기면_DELIVERED_와_48시간_뒤_상품_확인_기한이_기록된다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = shippedOrder(seller, buyer);
		Instant due = order.shipment().deliveryDueAt();

		assertThat(shipmentService.completeDueDeliveries(due.minusSeconds(1))).isZero();
		assertThat(shipmentService.completeDueDeliveries(due)).isEqualTo(1);

		PurchaseOrder delivered = orderRepository.findById(order.orderId()).orElseThrow();
		assertThat(delivered.getShippingStatus()).isEqualTo(ShippingStatus.DELIVERED);
		assertThat(delivered.getDeliveredAt()).isEqualTo(due);
		assertThat(delivered.getInspectionDeadlineAt()).isEqualTo(due.plus(Duration.ofHours(48)));
		// 배송 완료만으로 거래를 완료하거나 판매대금을 지급하지 않는다.
		assertThat(delivered.getTradeStatus()).isEqualTo(TradeStatus.IN_PROGRESS);
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).isEmpty();
	}

	@Test
	void F014_AC02_순차로_다시_실행해도_deliveredAt_과_상품_확인_기한이_바뀌지_않는다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = shippedOrder(seller, buyer);
		Instant due = order.shipment().deliveryDueAt();
		shipmentService.completeDueDeliveries(due);

		assertThat(shipmentService.completeDueDeliveries(due.plus(Duration.ofHours(5)))).isZero();

		PurchaseOrder delivered = orderRepository.findById(order.orderId()).orElseThrow();
		assertThat(delivered.getDeliveredAt()).isEqualTo(due);
		assertThat(delivered.getInspectionDeadlineAt()).isEqualTo(due.plus(Duration.ofHours(48)));
	}

	@Test
	void 발송_전_주문과_취소된_주문은_대상이_아니다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse waiting = purchase(buyer, product(seller, 30_000));
		String buyer2 = member("it_buyer2", "구매자2");
		OrderDetailResponse cancelled = purchase(buyer2, product(seller, 20_000));
		cancellationService.cancelByParty(buyer2, cancelled.orderId(), new OrderCancelRequest("변심"));

		assertThat(shipmentService.completeDueDeliveries(clock.instant().plus(Duration.ofDays(3)))).isZero();
		assertThat(orderRepository.findById(waiting.orderId()).orElseThrow().getShippingStatus())
				.isEqualTo(ShippingStatus.WAITING_SHIPMENT);
		assertThat(orderRepository.findById(cancelled.orderId()).orElseThrow().getDeliveredAt()).isNull();
		assertThat(shipmentRepository.count()).isZero();
	}

	@Test
	void 스케줄러는_모의_배송_완료도_실행한다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = shippedOrder(seller, buyer);
		clock.set(order.shipment().deliveryDueAt());

		newScheduler().run();

		assertThat(orderRepository.findById(order.orderId()).orElseThrow().getShippingStatus())
				.isEqualTo(ShippingStatus.DELIVERED);
	}
}
