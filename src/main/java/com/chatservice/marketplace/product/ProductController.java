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

import com.chatservice.marketplace.product.domain.ProductCategory;
import com.chatservice.marketplace.product.dto.ProductDetailResponse;
import com.chatservice.marketplace.product.dto.ProductRegisterRequest;
import com.chatservice.marketplace.product.dto.ProductSummaryResponse;
import com.chatservice.marketplace.product.service.IProductService;

import jakarta.validation.Valid;

/** 상품 등록·공개 조회(설계 5.2.1~5.2.3). 목록·상세 GET 은 apiFilterChain 에서 인증 없이 허용한다. */
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

    @GetMapping
    public List<ProductSummaryResponse> list(@RequestParam(name = "category", required = false) ProductCategory category) {
        return productService.listOnSale(category);
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse detail(@PathVariable("productId") Long productId) {
        return productService.getPublicDetail(productId);
    }
}
