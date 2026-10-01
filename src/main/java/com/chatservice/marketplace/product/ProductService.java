package com.chatservice.marketplace.product;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.MemberDirectory;

@Service
public class ProductService implements IProductService {

	private static final Logger log = LoggerFactory.getLogger(ProductService.class);

	private final ProductRepository productRepository;
	private final MemberDirectory memberDirectory;
	private final Clock clock;

	public ProductService(ProductRepository productRepository, MemberDirectory memberDirectory, Clock clock) {
		this.productRepository = productRepository;
		this.memberDirectory = memberDirectory;
		this.clock = clock;
	}

	@Override
	@Transactional
	public ProductDetailResponse register(String sellerId, ProductRegisterRequest request) {
		Product product = productRepository.save(Product.register(sellerId, request.name(), request.description(),
				request.category(), request.price(), clock.instant()));
		log.info("상품 등록 productId={} memberId={} status={}", product.getProductId(), sellerId, product.getStatus());
		return ProductDetailResponse.of(product, memberDirectory.nickname(sellerId));
	}
}
