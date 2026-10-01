package com.chatservice.marketplace.order;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** 주문 API(설계 명세서 5.2.13~5.2.20). */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final IOrderService orderService;
	private final IShipmentService shipmentService;
	private final IOrderCancellationService cancellationService;
	private final ITradeCompletionService completionService;
	private final IRefundService refundService;
	private final IOrderQueryService queryService;

	public OrderController(IOrderService orderService, IShipmentService shipmentService,
			IOrderCancellationService cancellationService, ITradeCompletionService completionService,
			IRefundService refundService, IOrderQueryService queryService) {
		this.orderService = orderService;
		this.shipmentService = shipmentService;
		this.cancellationService = cancellationService;
		this.completionService = completionService;
		this.refundService = refundService;
		this.queryService = queryService;
	}

	@PostMapping
	public ResponseEntity<OrderDetailResponse> placeOrder(@AuthenticationPrincipal String memberId,
			@Valid @RequestBody OrderPlaceRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(memberId, request));
	}

	@PostMapping("/{orderId}/shipment")
	public ResponseEntity<OrderDetailResponse> registerShipment(@AuthenticationPrincipal String memberId,
			@PathVariable("orderId") Long orderId, @Valid @RequestBody ShipmentRegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(shipmentService.registerShipment(memberId, orderId, request));
	}

	@PostMapping("/{orderId}/cancel")
	public OrderDetailResponse cancel(@AuthenticationPrincipal String memberId, @PathVariable("orderId") Long orderId,
			@Valid @RequestBody OrderCancelRequest request) {
		return cancellationService.cancelByParty(memberId, orderId, request);
	}

	@PostMapping("/{orderId}/confirm-receipt")
	public OrderDetailResponse confirmReceipt(@AuthenticationPrincipal String memberId,
			@PathVariable("orderId") Long orderId) {
		return completionService.confirmReceipt(memberId, orderId);
	}

	@PostMapping("/{orderId}/refund-request")
	public ResponseEntity<OrderDetailResponse> requestRefund(@AuthenticationPrincipal String memberId,
			@PathVariable("orderId") Long orderId, @Valid @RequestBody RefundRequestCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(refundService.requestRefund(memberId, orderId, request));
	}

	@PostMapping("/{orderId}/refund-request/approve")
	public OrderDetailResponse approveRefund(@AuthenticationPrincipal String memberId,
			@PathVariable("orderId") Long orderId) {
		return refundService.decide(memberId, orderId, true);
	}

	@PostMapping("/{orderId}/refund-request/reject")
	public OrderDetailResponse rejectRefund(@AuthenticationPrincipal String memberId,
			@PathVariable("orderId") Long orderId) {
		return refundService.decide(memberId, orderId, false);
	}

	@GetMapping("/purchases")
	public List<OrderSummaryResponse> purchases(@AuthenticationPrincipal String memberId) {
		return queryService.listPurchases(memberId);
	}

	@GetMapping("/sales")
	public List<OrderSummaryResponse> sales(@AuthenticationPrincipal String memberId) {
		return queryService.listSales(memberId);
	}

	@GetMapping("/{orderId}")
	public OrderDetailResponse detail(@AuthenticationPrincipal String memberId, @PathVariable("orderId") Long orderId) {
		return queryService.getDetail(memberId, orderId);
	}
}
