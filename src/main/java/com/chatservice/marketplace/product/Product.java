package com.chatservice.marketplace.product;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PRODUCT 테이블. 수량 1개의 판매 등록 건이다. */
@Entity
@Table(name = "PRODUCT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "PRODUCT_ID")
	private Long productId;

	@Column(name = "SELLER_ID", nullable = false)
	private String sellerId;

	@Column(name = "NAME", nullable = false, length = 100)
	private String name;

	@Lob
	@Column(name = "DESCRIPTION", nullable = false)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "CATEGORY", nullable = false, length = 30)
	private Category category;

	@Column(name = "PRICE", nullable = false)
	private long price;

	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false, length = 30)
	private ProductStatus status;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	@Column(name = "SOLD_AT")
	private Instant soldAt;

	public static Product register(String sellerId, String name, String description, Category category,
			long price, Instant now) {
		Product product = new Product();
		product.sellerId = sellerId;
		product.name = name;
		product.description = description;
		product.category = category;
		product.price = price;
		product.status = ProductStatus.ON_SALE;
		product.createdAt = now;
		return product;
	}

	public boolean isOnSale() {
		return status == ProductStatus.ON_SALE;
	}

	/** 결제 성공 시 판매 종료로 바꾼다. 판매 종료 시각은 결제 성공 시각과 같다. */
	public void markSold(Instant now) {
		this.status = ProductStatus.SOLD;
		this.soldAt = now;
	}
}
