package com.chatservice.marketplace.product;

import java.util.List;

public interface IProductService {

	/** 요청한 회원 명의로 판매 중 상품을 등록한다(F-001). */
	ProductDetailResponse register(String sellerId, ProductRegisterRequest request);

	/** 판매 중 상품 목록. category 가 null 이면 전체 카테고리다(F-002). */
	List<ProductSummaryResponse> listOnSale(Category category);

	/** 판매 중 상품 상세. 없거나 판매 종료된 상품은 PRODUCT_NOT_FOUND 다(F-002). */
	ProductDetailResponse getPublicDetail(Long productId);
}
