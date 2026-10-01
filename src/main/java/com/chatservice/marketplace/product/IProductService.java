package com.chatservice.marketplace.product;

public interface IProductService {

	/** 요청한 회원 명의로 판매 중 상품을 등록한다(F-001). */
	ProductDetailResponse register(String sellerId, ProductRegisterRequest request);
}
