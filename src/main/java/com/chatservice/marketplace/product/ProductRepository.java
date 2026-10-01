package com.chatservice.marketplace.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

	List<Product> findByStatusOrderByCreatedAtDescProductIdDesc(ProductStatus status);

	List<Product> findByStatusAndCategoryOrderByCreatedAtDescProductIdDesc(ProductStatus status, Category category);
}
