package com.chatservice.marketplace.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

	public OrderController(IOrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	public ResponseEntity<OrderDetailResponse> placeOrder(@AuthenticationPrincipal String memberId,
			@Valid @RequestBody OrderPlaceRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(memberId, request));
	}
}
