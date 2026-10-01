package com.chatservice.marketplace.product;

/** 상품 판매 상태. SOLD 는 ON_SALE 로 돌아가지 않는다(BR-002). */
public enum ProductStatus {
	ON_SALE,
	SOLD
}
