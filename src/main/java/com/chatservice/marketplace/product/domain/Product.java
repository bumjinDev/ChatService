package com.chatservice.marketplace.product.domain;

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

/**
 * 판매 등록 건(PRODUCT).
 *
 * 상품 정보·최초 수량은 등록 후 바꾸지 않는다(상품 수정·재입고는 범위 밖). 남은 수량은 결제 성공 시에만 줄어들고,
 * 취소·환불이 있어도 다시 늘리지 않는다(BR-002). 다른 엔티티와는 식별자 값으로만 연결한다(설계 4.1).
 */
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

    @Column(name = "NAME", nullable = false)
    private String name;

    @Lob
    @Column(name = "DESCRIPTION", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", nullable = false)
    private ProductCategory category;

    @Column(name = "PRICE", nullable = false)
    private long price;

    @Column(name = "INITIAL_QUANTITY", nullable = false)
    private long initialQuantity;

    @Column(name = "REMAINING_QUANTITY", nullable = false)
    private long remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private ProductStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "SOLD_AT")
    private Instant soldAt;

    public static Product register(String sellerId, String name, String description, ProductCategory category,
                                   long price, long quantity, Instant now) {
        Product product = new Product();
        product.sellerId = sellerId;
        product.name = name;
        product.description = description;
        product.category = category;
        product.price = price;
        product.initialQuantity = quantity;
        product.remainingQuantity = quantity;
        product.status = ProductStatus.ON_SALE;
        product.createdAt = now;
        return product;
    }

    public boolean isOnSale() {
        return status == ProductStatus.ON_SALE;
    }

    public boolean isSoldBy(String memberId) {
        return sellerId.equals(memberId);
    }

    /**
     * 결제 성공 시 주문 수량만큼 남은 수량을 줄인다. 0 이 되는 순간에만 판매 종료하고 그 시각을 기록한다.
     * 호출 전에 판매 중 여부와 수량 충분 여부를 검사해야 한다.
     */
    public void deductStock(long quantity, Instant now) {
        if (!isOnSale() || quantity < 1 || quantity > remainingQuantity) {
            throw new IllegalStateException("재고 차감 조건을 만족하지 않습니다. productId=" + productId);
        }
        remainingQuantity -= quantity;
        if (remainingQuantity == 0) {
            status = ProductStatus.SOLD;
            soldAt = now;
        }
    }
}
