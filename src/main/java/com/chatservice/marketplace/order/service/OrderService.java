package com.chatservice.marketplace.order.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.AmountRules;
import com.chatservice.marketplace.common.error.BusinessException;
import com.chatservice.marketplace.common.error.ErrorCode;
import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.conversation.service.IConversationService;
import com.chatservice.marketplace.offer.domain.PriceOffer;
import com.chatservice.marketplace.offer.service.IOfferService;
import com.chatservice.marketplace.order.PurchaseOrderRepository;
import com.chatservice.marketplace.order.domain.OrderEnums.PriceSource;
import com.chatservice.marketplace.order.domain.PurchaseOrder;
import com.chatservice.marketplace.order.dto.PlaceOrderRequest;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.service.IProductService;
import com.chatservice.marketplace.wallet.service.IWalletService;

@Service
public class OrderService implements IOrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final IProductService productService;
    private final IOfferService offerService;
    private final IWalletService walletService;
    private final IConversationService conversationService;
    private final TimeRules timeRules;

    public OrderService(PurchaseOrderRepository purchaseOrderRepository, IProductService productService,
                        IOfferService offerService, IWalletService walletService,
                        IConversationService conversationService, TimeRules timeRules) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productService = productService;
        this.offerService = offerService;
        this.walletService = walletService;
        this.conversationService = conversationService;
        this.timeRules = timeRules;
    }

    /*
     * 처리 순서(설계 6.10)
     *  1) 상품(404) → 본인 상품(403) → 판매 중(409) → 남은 수량 이상 요청(409 INSUFFICIENT_STOCK)
     *  2) 적용 단가: offerId 가 없으면 등록 가격(LISTED), 있으면 본인에게 수락된 같은 상품의 제안 금액(AGREED)
     *  3) 총 결제액 = 단가 × 수량. 저장·연산 범위를 넘으면 400. 잔액이 부족하면 409
     *  4) 주문 생성 → 잔액 차감과 PURCHASE 내역(주문 참조) → 주문 수량만 재고 차감(0 이면 SOLD) → 당사자 대화가 없으면 생성
     *  5) 상품의 모든 대화에 CONVERSATION_STATE 전달(커밋 후)
     * 4) 의 변경은 한 트랜잭션으로 반영한다. 모든 검사는 변경 전에 끝나므로 실패한 요청은 주문·차감·재고를 바꾸지 않는다.
     * 동시 결제에서 재고·잔액을 넘지 않는 보장과 같은 결제 재시도의 중복 방지는 후속 과제다.
     */
    /**
     * 구매자의 잔액으로 상품을 결제하고 주문을 확정한다.
     *
     * 상품·제안·잔액 검사를 모두 마친 뒤 주문 생성, 잔액 차감과 구매 내역 기록, 재고 차감,
     * 당사자 대화 생성을 하나의 트랜잭션으로 반영한다. 대화 상태 이벤트는 커밋 후 전송된다.
     *
     * @param buyerId 결제를 요청한 회원 ID(인증 정보에서 얻은 값)
     * @param request 결제 요청. 상품 ID, 구매 수량, 수령인, 배송 주소와 선택 값인 합의 제안 ID, 요청 식별자를 담는다
     * @return 생성된 주문의 ID
     * @throws BusinessException 다음 경우에 던지며, 이때 데이터는 변경되지 않는다.
     *           - {@code PRODUCT_NOT_FOUND}: 상품이 없다
     *           - {@code SELF_TRADE_NOT_ALLOWED}: 본인 상품을 구매하려 한다
     *           - {@code PRODUCT_NOT_ON_SALE}: 판매 종료된 상품이다
     *           - {@code INSUFFICIENT_STOCK}: 구매 수량이 남은 수량보다 많다
     *           - {@code INVALID_OFFER_SELECTION}: 선택한 제안이 이 상품·구매자에게 수락된 제안이 아니다
     *           - {@code VALIDATION_ERROR}: 총 결제액이 저장 가능한 범위를 넘는다
     *           - {@code INSUFFICIENT_BALANCE}: 잔액이 총 결제액보다 적다
     */
    @Override
    @Transactional
    public Long placeOrder(String buyerId, PlaceOrderRequest request) {
        /*
         * [포트폴리오 작업 대상] 동시 결제 시 재고·잔액 정합성
         * - 업무 규칙: 구매 수량 합계는 재고를, 결제 금액 합계는 잔액을 넘을 수 없다.
         * - 현재 구조: 상품·지갑을 잠금 없이 읽고, Java 에서 계산한 값으로 커밋 시점에 UPDATE 한다.
         *   두 결제가 같은 값을 읽으면 갱신 손실로 초과 판매·초과 사용이 생길 수 있다(코드 기준 추론, 재현 전).
         * - 작업: 동시 결제 재현 → SQL 로그와 결과 측정 → 대안 비교 → 결정.
         * - 함께 판단할 것: 상품·지갑 두 행의 잠금 순서, 트랜잭션 안의 대화 생성·이벤트 계산이 잠금 유지 시간에 주는 영향.
         * - 정리 문서: docs/5. 병목지점/1. 병목예상지점정리/1. 결제 재고·잔액 동시성(placeOrder).md
         */
        // 결제 요청이 도착해서 과정을 시작하는 시각. 구매 확정 시각, 발송 기한 계산, 잔액 내역 시각에 같은 값을 쓴다
        Instant now = timeRules.now();
        long quantity = request.quantity();

        // 상품 조회. 없으면 PRODUCT_NOT_FOUND
        Product product = productService.getProduct(request.productId());
        // 본인 상품은 구매할 수 없다
        if (product.isSoldBy(buyerId)) {
            throw new BusinessException(ErrorCode.SELF_TRADE_NOT_ALLOWED);
        }
        // 판매 종료 상품은 구매할 수 없다
        if (!product.isOnSale()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        // 남은 수량을 넘는 구매는 거절한다
        if (quantity > product.getRemainingQuantity()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }

        /* 거래 안되는 기준을 전부 통과 후에 실제로 거래를 시작하는 위치 */

        // 적용 단가와 가격 출처. 합의 가격을 쓸 때만 제안 ID 를 남긴다
        long unitPrice;
        PriceSource priceSource;
        Long offerId = null;

        /* 제안된 가격이 아니라 정가를 선택 */
        if (request.offerId() == null) {

            unitPrice = product.getPrice();
            priceSource = PriceSource.LISTED;
        }
        /* 정각가 아닌 판매자와 구매자간에 이미 제안으로 합의된 가격을 옵션으로 선택 */
        else {
            // 이 상품·구매자에게 수락된 제안인지 검증한 뒤 합의 가격을 적용한다
            PriceOffer offer = offerService.acceptedOfferFor(request.offerId(), product.getProductId(), buyerId);   // 해당 상품에 대한 판매자와 구매자 간 제안 내역(수락된 제안 내역만 가져오기)을 가져온다.
            unitPrice = offer.getAmount();         // 수락 제안한 금액
            priceSource = PriceSource.AGREED;      // 수락이라는 상태 값으로 로드.(PriceSource 은 EUME 객체로 LISTED 와 AGREED 두 가지 값만 가짐)
            offerId = offer.getOfferId();          // 수락 제안한 고유 식별 번호.
        }

        // 총 결제액 = 단가 × 수량. 저장 범위를 넘으면 VALIDATION_ERROR
        long paidAmount = AmountRules.totalAmount(unitPrice, quantity, "quantity");
        // 잔액 사전 검사. 지갑이 없으면 잔액 0 으로 본다
        if (walletService.currentBalance(buyerId) < paidAmount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        // 주문 데이터들을 생성 하는 단계

        // 주문 내역 데이터 생성(테이블 "PURCHASE_ORDER") : 주문 생성(발송 대기, 진행 중). IDENTITY 채번이라 저장 시점에 INSERT 되어 주문 ID 가 정해진다
        PurchaseOrder order = purchaseOrderRepository.save(PurchaseOrder.place(product.getProductId(), buyerId,
                product.getSellerId(), quantity, unitPrice, paidAmount, priceSource, offerId,
                request.recipientName(), request.shippingAddress(), now, timeRules.shipDeadlineAt(now),
                request.requestId()));
        // 구매자의 잔액 차감(테이블 "WALLET") : 잔액 차감과 PURCHASE 내역 기록. 내역이 주문 ID 를 참조하므로 주문 생성 뒤에 호출한다
        walletService.debitForPurchase(buyerId, paidAmount, order.getOrderId(), now);
        /*
         * [검토 메모] 상품 모듈과의 결합
         * - 같은 서비스 계층의 도메인 간 의존이다. 결제와 재고 차감을 함께 반영해야 하므로 호출 자체는 필요하다.
         * - 상품 ID 가 아니라 Product 엔티티를 넘긴다. 상품 모듈의 내부 표현이 주문 모듈에 노출된다.
         * - deductStock 은 save 없이 dirty checking 에 의존한다. 전달한 엔티티가 현재 트랜잭션에서 관리되는 상태여야 반영된다.
         * - 재고 검사가 이 메서드(남은 수량 비교)와 Product.deductStock 양쪽에 있다.
         * - 동시 결제 대응으로 재고 차감 방식을 바꾸면 이 시그니처와 위 사전 검사도 함께 재검토한다.
         */
        // 주문 수량만큼 재고 차감. 남은 수량이 0 이 되면 판매 종료로 바뀐다
        productService.deductStock(product, quantity, now);
        // 이 상품에 대한 구매자의 대화가 없으면 시스템이 만든다
        conversationService.ensureTradeConversation(product, buyerId, now);

        logger.info("[결제 성공] orderId={}, memberId={}, productId={}, quantity={}, paidAmount={}, "
                        + "tradeStatus=null -> {}, shippingStatus=null -> {}",
                order.getOrderId(), buyerId, product.getProductId(), quantity, paidAmount,
                order.getTradeStatus(), order.getShippingStatus());

        // 상품의 모든 대화에 보낼 상태 이벤트를 등록한다. 실제 전송은 커밋 후에 한다
        conversationService.publishStateToProductConversations(product);
        // 메서드가 반환되면 트랜잭션이 커밋된다
        return order.getOrderId();
    }
}
