package com.chatservice.marketplace.product;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** 상품 API(설계 명세서 5.2.1~5.2.3). */
@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final IProductService productService;

	public ProductController(IProductService productService) {
		this.productService = productService;
	}

	@PostMapping
	public ResponseEntity<ProductDetailResponse> register(@AuthenticationPrincipal String memberId,
			@Valid @RequestBody ProductRegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(productService.register(memberId, request));
	}
}
