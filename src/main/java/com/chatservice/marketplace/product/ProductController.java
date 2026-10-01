package com.chatservice.marketplace.product;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

	/** 공개 목록. 인증이 필요 없다. category 에 다섯 값이 아닌 값을 보내면 400 이다. */
	@GetMapping
	public List<ProductSummaryResponse> list(@RequestParam(name = "category", required = false) Category category) {
		return productService.listOnSale(category);
	}

	/** 공개 상세. 판매 중 상품만 제공한다. */
	@GetMapping("/{productId}")
	public ProductDetailResponse detail(@PathVariable("productId") Long productId) {
		return productService.getPublicDetail(productId);
	}
}
