package com.chatservice.marketplace.product.service;

import java.time.Instant;
import java.util.List;

import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.domain.ProductCategory;
import com.chatservice.marketplace.product.dto.ProductDetailResponse;
import com.chatservice.marketplace.product.dto.ProductRegisterRequest;
import com.chatservice.marketplace.product.dto.ProductSummaryResponse;

public interface IProductService {

    /** F-001 상품 등록. */
    ProductDetailResponse register(String sellerId, ProductRegisterRequest request);

    /** F-002 공개 목록. category 가 null 이면 전체. */
    List<ProductSummaryResponse> listOnSale(ProductCategory category);

    /** F-002 공개 상세. 없거나 판매 종료된 상품은 PRODUCT_NOT_FOUND. */
    ProductDetailResponse getPublicDetail(Long productId);

    /** 다른 기능이 상품을 판매 상태와 관계없이 읽을 때 쓴다. 없으면 PRODUCT_NOT_FOUND. */
    Product getProduct(Long productId);

    /** 결제 성공 시 주문 수량만 차감하고 남은 수량이 0 이면 판매 종료한다(F-010). 호출하는 쪽의 트랜잭션에서 실행된다. */
    void deductStock(Product product, long quantity, Instant now);
}
