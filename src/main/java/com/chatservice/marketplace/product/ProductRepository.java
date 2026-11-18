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

    /* 상품 한 행을 비관적 쓰기 잠금으로 조회한다(SELECT ... FOR UPDATE). 다른 트랜잭션이 같은 행의 잠금을 가지고 있으면 그 트랜잭션이 커밋되거나 롤백될 때까지 기다린다. 잠금 대기 제한 시간은 두지 않는다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId = :productId")
    Optional<Product> findByIdForUpdate(@Param("productId") Long productId);
}
