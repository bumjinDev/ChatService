package com.chatservice.marketplace.product;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
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

	@Override
	@Transactional(readOnly = true)
	public List<ProductSummaryResponse> listOnSale(Category category) {
		List<Product> products = (category == null)
				? productRepository.findByStatusOrderByCreatedAtDescProductIdDesc(ProductStatus.ON_SALE)
				: productRepository.findByStatusAndCategoryOrderByCreatedAtDescProductIdDesc(ProductStatus.ON_SALE,
						category);
		Map<String, String> nicknames = memberDirectory.nicknames(products.stream().map(Product::getSellerId).toList());
		return products.stream()
				.map(p -> ProductSummaryResponse.of(p, nicknames.get(p.getSellerId())))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public ProductDetailResponse getPublicDetail(Long productId) {
		Product product = productRepository.findById(productId)
				.filter(Product::isOnSale)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		return ProductDetailResponse.of(product, memberDirectory.nickname(product.getSellerId()));
	}
}
