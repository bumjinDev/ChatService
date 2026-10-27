package com.chatservice.marketplace.product.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.error.BusinessException;
import com.chatservice.marketplace.common.error.ErrorCode;
import com.chatservice.marketplace.common.member.MemberDirectory;
import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.product.ProductRepository;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.domain.ProductCategory;
import com.chatservice.marketplace.product.domain.ProductStatus;
import com.chatservice.marketplace.product.dto.ProductDetailResponse;
import com.chatservice.marketplace.product.dto.ProductRegisterRequest;
import com.chatservice.marketplace.product.dto.ProductSummaryResponse;
import com.chatservice.marketplace.reservation.service.IReservationQueryService;

@Service
public class ProductService implements IProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final MemberDirectory memberDirectory;
    private final IReservationQueryService reservationQueryService;
    private final TimeRules timeRules;

    public ProductService(ProductRepository productRepository, MemberDirectory memberDirectory,
                          IReservationQueryService reservationQueryService, TimeRules timeRules) {
        this.productRepository = productRepository;
        this.memberDirectory = memberDirectory;
        this.reservationQueryService = reservationQueryService;
        this.timeRules = timeRules;
    }

    @Override
    @Transactional
    public ProductDetailResponse register(String sellerId, ProductRegisterRequest request) {
        Product product = productRepository.save(Product.register(sellerId, request.name(), request.description(),
                request.category(), request.price(), request.quantity(), timeRules.now()));
        logger.info("[상품 등록] productId={}, memberId={}, quantity={}, status={}",
                product.getProductId(), sellerId, product.getInitialQuantity(), product.getStatus());
        // 막 등록한 상품에는 예약이 없으므로 구매 가능 수량은 남은 수량과 같다.
        return ProductDetailResponse.of(product, memberDirectory.nickname(sellerId), product.getRemainingQuantity());
    }

    /*
     * 판매 중 조건과 정렬은 예약과 관계없다. 구매 가능 수량이 0 인 상품도 목록에 그대로 나온다(예약 중인 상품을 숨기지 않는다).
     * 구매 가능 수량은 상품 ID 목록으로 활성 예약을 한 번에 조회해 상품마다 계산한다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> listOnSale(ProductCategory category, String viewerId) {
        List<Product> products = (category == null)
                ? productRepository.findByStatusAndRemainingQuantityGreaterThanOrderByCreatedAtDescProductIdDesc(
                        ProductStatus.ON_SALE, 0)
                : productRepository.findByStatusAndRemainingQuantityGreaterThanAndCategoryOrderByCreatedAtDescProductIdDesc(
                        ProductStatus.ON_SALE, 0, category);
        Map<String, String> nicknames = memberDirectory.nicknames(
                products.stream().map(Product::getSellerId).collect(Collectors.toSet()));
        Map<Long, Long> availableQuantities = reservationQueryService.availableQuantities(products, viewerId);
        return products.stream()
                .map(product -> ProductSummaryResponse.of(product, nicknames.get(product.getSellerId()),
                        availableQuantities.get(product.getProductId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getPublicDetail(Long productId, String viewerId) {
        Product product = productRepository.findById(productId)
                .filter(Product::isOnSale)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductDetailResponse.of(product, memberDirectory.nickname(product.getSellerId()),
                reservationQueryService.availableQuantity(product, viewerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Override
    @Transactional
    public void deductStock(Product product, long quantity, Instant now) {
        long before = product.getRemainingQuantity();
        product.deductStock(quantity, now);
        logger.info("[재고 차감] productId={}, remaining {} -> {}, status={}",
                product.getProductId(), before, product.getRemainingQuantity(), product.getStatus());
    }

    @Override
    @Transactional
    public void restoreStock(Product product, long quantity) {
        long before = product.getRemainingQuantity();
        product.restoreStock(quantity);
        logger.info("[재고 복구] productId={}, remaining {} -> {}, status={}",
                product.getProductId(), before, product.getRemainingQuantity(), product.getStatus());
    }
}
