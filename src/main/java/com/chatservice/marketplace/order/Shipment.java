package com.chatservice.marketplace.order;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** SHIPMENT 테이블. 주문당 한 번 등록하는 발송 정보이며 등록 후 바꾸지 않는다. */
@Entity
@Table(name = "SHIPMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "SHIPMENT_ID")
	private Long shipmentId;

	@Column(name = "ORDER_ID", nullable = false)
	private Long orderId;

	@Column(name = "CARRIER_NAME", nullable = false, length = 100)
	private String carrierName;

	@Column(name = "TRACKING_NUMBER", nullable = false, length = 100)
	private String trackingNumber;

	@Column(name = "SHIPPED_AT", nullable = false)
	private Instant shippedAt;

	@Column(name = "DELIVERY_DUE_AT", nullable = false)
	private Instant deliveryDueAt;

	@Column(name = "REQUEST_ID", length = 64)
	private String requestId;

	public static Shipment register(Long orderId, String carrierName, String trackingNumber, Instant shippedAt,
			Instant deliveryDueAt, String requestId) {
		Shipment shipment = new Shipment();
		shipment.orderId = orderId;
		shipment.carrierName = carrierName;
		shipment.trackingNumber = trackingNumber;
		shipment.shippedAt = shippedAt;
		shipment.deliveryDueAt = deliveryDueAt;
		shipment.requestId = requestId;
		return shipment;
	}
}
