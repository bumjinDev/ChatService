package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.support.OrderTestSupport;
import com.chatservice.marketplace.support.TestTimes;

/** F-011 발송 정보 등록 검증. */
class ShipmentRegisterTest extends OrderTestSupport {

	@Autowired
	private IShipmentService shipmentService;

	@Autowired
	private ShipmentRepository shipmentRepository;

	private OrderDetailResponse paidOrder(String seller, String buyer) {
		Product product = product(seller, 30_000);
		return purchase(buyer, product);
	}

	@Test
	void F011_AC01_기한_안의_발송_대기_주문에_처음_등록하면_201_SHIPPING_과_모의_배송_완료_예정_시각() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = paidOrder(seller, buyer);
		clock.advance(Duration.ofHours(3));

		mockMvc.perform(post("/api/orders/" + order.orderId() + "/shipment").cookie(authCookie(seller))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"carrierName\":\"우체국\",\"trackingNumber\":\"1234-5678\",\"requestId\":\"s-1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shippingStatus").value("SHIPPING"))
				.andExpect(jsonPath("$.tradeStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.myRole").value("SELLER"))
				.andExpect(jsonPath("$.shipment.carrierName").value("우체국"))
				.andExpect(jsonPath("$.shipment.trackingNumber").value("1234-5678"))
				.andExpect(jsonPath("$.shipment.shippedAt").value(clock.instant().toString()))
				.andExpect(jsonPath("$.shipment.deliveryDueAt")
						.value(clock.instant().plus(Duration.ofMinutes(2)).toString()));

		Shipment shipment = shipmentRepository.findFirstByOrderIdOrderByShipmentIdAsc(order.orderId()).orElseThrow();
		assertThat(shipment.getDeliveryDueAt()).isEqualTo(shipment.getShippedAt().plus(Duration.ofMinutes(2)));
		assertThat(shipment.getRequestId()).isEqualTo("s-1");
		assertThat(orderRepository.findById(order.orderId()).orElseThrow().getShippingStatus())
				.isEqualTo(ShippingStatus.SHIPPING);
	}

	@Test
	void F011_AC02_월요일_확정_주문은_다음_화요일_0시_KST_부터_등록할_수_없다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = paidOrder(seller, buyer); // 2026-09-28(월) 10:00 KST 확정

		clock.set(TestTimes.kst(2026, 10, 6, 0, 0));
		assertThat(errorOf(() -> shipmentService.registerShipment(seller, order.orderId(),
				new ShipmentRegisterRequest("우체국", "1", null)))).isEqualTo(ErrorCode.SHIPMENT_DEADLINE_PASSED);
		assertThat(shipmentRepository.count()).isZero();
		assertThat(orderRepository.findById(order.orderId()).orElseThrow().getShippingStatus())
				.isEqualTo(ShippingStatus.WAITING_SHIPMENT);
	}

	@Test
	void 발송_기한_직전인_다음_월요일_23시59분에는_등록할_수_있다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = paidOrder(seller, buyer);

		clock.set(TestTimes.kst(2026, 10, 5, 23, 59));
		OrderDetailResponse shipped = shipmentService.registerShipment(seller, order.orderId(),
				new ShipmentRegisterRequest("우체국", "1", null));
		assertThat(shipped.shippingStatus()).isEqualTo(ShippingStatus.SHIPPING);
	}

	@Test
	void F011_AC03_등록한_뒤_다른_운송장을_입력하면_409_ALREADY_SHIPPED_이고_원래_정보가_유지된다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = paidOrder(seller, buyer);
		shipmentService.registerShipment(seller, order.orderId(), new ShipmentRegisterRequest("우체국", "AAA", null));

		assertThat(errorOf(() -> shipmentService.registerShipment(seller, order.orderId(),
				new ShipmentRegisterRequest("한진", "BBB", null)))).isEqualTo(ErrorCode.ALREADY_SHIPPED);
		assertThat(shipmentRepository.findAll()).singleElement().satisfies(s -> {
			assertThat(s.getCarrierName()).isEqualTo("우체국");
			assertThat(s.getTrackingNumber()).isEqualTo("AAA");
		});
	}

	@Test
	void 판매자가_아니면_403_취소된_주문은_409_ORDER_NOT_IN_PROGRESS() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = paidOrder(seller, buyer);

		mockMvc.perform(post("/api/orders/" + order.orderId() + "/shipment").cookie(authCookie(buyer))
				.contentType(MediaType.APPLICATION_JSON).content("{\"carrierName\":\"a\",\"trackingNumber\":\"b\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_SELLER"));

		PurchaseOrder entity = orderRepository.findById(order.orderId()).orElseThrow();
		entity.cancel(CancelledBy.BUYER, "변심", clock.instant());
		orderRepository.saveAndFlush(entity);
		mockMvc.perform(post("/api/orders/" + order.orderId() + "/shipment").cookie(authCookie(seller))
				.contentType(MediaType.APPLICATION_JSON).content("{\"carrierName\":\"a\",\"trackingNumber\":\"b\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORDER_NOT_IN_PROGRESS"));
		mockMvc.perform(post("/api/orders/999999999/shipment").cookie(authCookie(seller))
				.contentType(MediaType.APPLICATION_JSON).content("{\"carrierName\":\"a\",\"trackingNumber\":\"b\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
		assertThat(shipmentRepository.count()).isZero();
	}

	@Test
	void 택배사명이나_운송장_번호가_비면_400() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse order = paidOrder(seller, buyer);

		mockMvc.perform(post("/api/orders/" + order.orderId() + "/shipment").cookie(authCookie(seller))
				.contentType(MediaType.APPLICATION_JSON).content("{\"carrierName\":\" \",\"trackingNumber\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.carrierName").exists())
				.andExpect(jsonPath("$.fieldErrors.trackingNumber").exists());
		assertThat(shipmentRepository.count()).isZero();
	}
}
