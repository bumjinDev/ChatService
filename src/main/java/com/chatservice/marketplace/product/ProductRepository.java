package com.chatservice.marketplace.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.domain.ProductCategory;
import com.chatservice.marketplace.product.domain.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /* 공개 목록: STATUS = ON_SALE AND REMAINING_QUANTITY > 0, 등록 시각 내림차순(같은 시각이면 식별자 내림차순) */
    List<Product> findByStatusAndRemainingQuantityGreaterThanOrderByCreatedAtDescProductIdDesc(
            ProductStatus status, long remainingQuantity);

    List<Product> findByStatusAndRemainingQuantityGreaterThanAndCategoryOrderByCreatedAtDescProductIdDesc(
            ProductStatus status, long remainingQuantity, ProductCategory category);
}
