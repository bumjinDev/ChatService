package com.chatservice.marketplace.product;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
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

/**
 * 상품 등록·공개 조회(설계 5.2.1~5.2.3). 목록·상세 GET 은 apiFilterChain 에서 인증 없이 허용한다.
 * 목록·상세의 구매 가능 수량은 조회한 회원 기준으로 계산하므로, 로그인한 회원이면 그 ID 를 서비스에 넘긴다.
 */
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
    public List<ProductSummaryResponse> list(@RequestParam(name = "category", required = false) ProductCategory category,
                                             Authentication authentication) {
        return productService.listOnSale(category, viewerId(authentication));
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse detail(@PathVariable("productId") Long productId, Authentication authentication) {
        return productService.getPublicDetail(productId, viewerId(authentication));
    }

    /**
     * 조회한 회원 ID. 쿠키 없이 보낸 요청은 JwtAuthProcessorFilter 가 인증 정보를 넣지 않아 Spring Security 의 익명 인증이 되므로
     * null 을 돌려준다. 로그인하지 않은 조회의 구매 가능 수량은 모든 활성 예약을 뺀 값이 된다.
     */
    private static String viewerId(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof String memberId)) {
            return null;
        }
        return memberId;
    }
}
