package com.chatservice.marketplace.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.domain.ProductCategory;
import com.chatservice.marketplace.product.domain.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /* 공개 목록: STATUS = ON_SALE AND REMAINING_QUANTITY > 0, 등록 시각 내림차순(같은 시각이면 식별자 내림차순) */
    List<Product> findByStatusAndRemainingQuantityGreaterThanOrderByCreatedAtDescProductIdDesc(
            ProductStatus status, long remainingQuantity);

    List<Product> findByStatusAndRemainingQuantityGreaterThanAndCategoryOrderByCreatedAtDescProductIdDesc(
            ProductStatus status, long remainingQuantity, ProductCategory category);

    /* 상품 한 행을 낙관적 락으로 조회한다. 조회할 때 행 잠금을 얻지 않는다. 이 쿼리로 조회한 상품은 트랜잭션이 상품의 값을 바꾸지 않아도 커밋 직전에 버전이 1 올라간다. 조회한 다음에 다른 트랜잭션이 상품의 버전을 바꿨으면 커밋하지 못하고 예외가 난다 */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select p from Product p where p.productId = :productId")
    Optional<Product> findByIdWithOptimisticLock(@Param("productId") Long productId);
}
