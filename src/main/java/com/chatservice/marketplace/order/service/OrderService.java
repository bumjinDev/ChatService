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
import com.chatservice.marketplace.offer.service.IAgreementQueryService;
import com.chatservice.marketplace.offer.service.IOfferService;
import com.chatservice.marketplace.order.PurchaseOrderRepository;
import com.chatservice.marketplace.order.domain.OrderEnums.PriceSource;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.PurchaseOrder;
import com.chatservice.marketplace.order.dto.PlaceOrderRequest;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.service.IProductService;
import com.chatservice.marketplace.reservation.service.IReservationQueryService;
import com.chatservice.marketplace.reservation.service.IReservationService;
import com.chatservice.marketplace.testsupport.PausePoint;
import com.chatservice.marketplace.testsupport.TestPause;
import com.chatservice.marketplace.testsupport.TradeTestLog;
import com.chatservice.marketplace.wallet.service.IWalletService;

@Service
public class OrderService implements IOrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final IProductService productService;
    private final IOfferService offerService;
    private final IWalletService walletService;
    private final IConversationService conversationService;
    private final IReservationService reservationService;
    private final IReservationQueryService reservationQueryService;
    private final IAgreementQueryService agreementQueryService;
    private final TimeRules timeRules;
    private final TestPause testPause;

    public OrderService(PurchaseOrderRepository purchaseOrderRepository, IProductService productService,
                        IOfferService offerService, IWalletService walletService,
                        IConversationService conversationService, IReservationService reservationService,
                        IReservationQueryService reservationQueryService, IAgreementQueryService agreementQueryService,
                        TimeRules timeRules, TestPause testPause) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productService = productService;
        this.offerService = offerService;
        this.walletService = walletService;
        this.conversationService = conversationService;
        this.reservationService = reservationService;
        this.reservationQueryService = reservationQueryService;
        this.agreementQueryService = agreementQueryService;
        this.timeRules = timeRules;
        this.testPause = testPause;
    }

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
     *           - {@code PRODUCT_RESERVED}: 구매 수량이 다른 회원의 예약을 뺀 구매 가능 수량보다 많다
     *           - {@code AGREED_OFFER_REQUIRED}: 합의가 예약 중인데 제안 없이 등록 가격으로 결제하려 한다
     *           - {@code INVALID_OFFER_SELECTION}: 선택한 제안이 이 상품·구매자에게 수락된 제안이 아니다
     *           - {@code OFFER_ALREADY_USED}: 선택한 제안으로 만든 취소되지 않은 주문이 이미 있다
     *           - {@code OFFER_RESERVATION_ENDED}: 선택한 제안의 예약이 만료되거나 해제되었다
     *           - {@code OFFER_QUANTITY_MISMATCH}: 구매 수량이 선택한 제안의 수량과 다르다
     *           - {@code VALIDATION_ERROR}: 총 결제액이 저장 가능한 범위를 넘는다
     *           - {@code INSUFFICIENT_BALANCE}: 잔액이 총 결제액보다 적다
     */
    @Override
    @Transactional
    public Long placeOrder(String buyerId, PlaceOrderRequest request) {
        TradeTestLog.registerTransactionEnd(logger, "결제", "OrderService.java", "placeOrder",
                "상품ID", request.productId(), "구매자ID", buyerId);
        TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "결제시작",
                "상품ID", request.productId(), "구매자ID", buyerId, "요청수량", request.quantity(),
                "제안ID", request.offerId() == null ? "-" : request.offerId());
        try {
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
            TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "남은수량판단",
                    "상품ID", product.getProductId(), "구매자ID", buyerId, "요청수량", quantity,
                    "남은수량", product.getRemainingQuantity(),
                    "판단결과", quantity > product.getRemainingQuantity() ? "거절" : "통과");
            // 남은 수량을 넘는 구매는 거절한다
            if (quantity > product.getRemainingQuantity()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
            }
            // 남은 수량 안이지만 다른 회원에게 걸린 활성 예약 때문에 구매 가능 수량을 넘는 구매는 거절한다.
            // 결제하는 회원 본인에게 걸린 예약은 빼지 않으므로 예약된 회원은 예약 수량까지 결제할 수 있다.
            long available = reservationQueryService.availableQuantity(product, buyerId);
            TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "구매가능수량판단",
                    "상품ID", product.getProductId(), "구매자ID", buyerId, "요청수량", quantity,
                    "남은수량", TradeTestLog.calculatedRemaining(),
                    "유효예약합계", TradeTestLog.calculatedReservedSum(), "구매가능수량", available,
                    "판단기준시각", TradeTestLog.calculatedAt(), "판단결과", quantity > available ? "거절" : "통과");
            if (quantity > available) {
                throw new BusinessException(ErrorCode.PRODUCT_RESERVED);
            }

            /* 거래 안되는 기준을 전부 통과 후에 실제로 거래를 시작하는 위치 */

            // 적용 단가와 가격 출처. 합의 가격을 쓸 때만 제안 ID 를 남긴다
            long unitPrice;
            PriceSource priceSource;
            Long offerId = null;

            /* 제안된 가격이 아니라 정가를 선택 */
            if (request.offerId() == null) {
                // 합의가 예약 중인 구매 희망자는 등록 가격으로 결제할 수 없고 합의 가격으로 먼저 결제한다
                if (agreementQueryService.hasActiveReservation(product.getProductId(), buyerId)) {
                    throw new BusinessException(ErrorCode.AGREED_OFFER_REQUIRED);
                }
                unitPrice = product.getPrice();
                priceSource = PriceSource.LISTED;
            }
            /* 정각가 아닌 판매자와 구매자간에 이미 제안으로 합의된 가격을 옵션으로 선택 */
            else {
                // 이 상품·구매자에게 수락된 제안인지 검증한 뒤 합의 가격을 적용한다
                PriceOffer offer = offerService.acceptedOfferFor(request.offerId(), product.getProductId(), buyerId);   // 해당 상품에 대한 판매자와 구매자 간 제안 내역(수락된 제안 내역만 가져오기)을 가져온다.
                // 합의는 한 번 결제할 수 있다. 그 제안으로 만든 주문이 취소되었으면 다시 결제할 수 있다
                if (purchaseOrderRepository.existsByOfferIdAndTradeStatusNot(offer.getOfferId(), TradeStatus.CANCELLED)) {
                    throw new BusinessException(ErrorCode.OFFER_ALREADY_USED);
                }
                // 결제 전 예약이 만료되거나 해제되었으면, 또는 취소로 만들어진 새 예약이 만료되거나 해제되었으면 합의 가격으로 결제할 수 없다
                if (!agreementQueryService.hasActiveReservationForOffer(offer.getOfferId())) {
                    throw new BusinessException(ErrorCode.OFFER_RESERVATION_ENDED);
                }
                // 합의 가격은 제안 수량과 같은 수량에만 적용한다. 한 주문 안에서 두 가격을 섞지 않는다
                if (quantity != offer.getQuantity()) {
                    throw new BusinessException(ErrorCode.OFFER_QUANTITY_MISMATCH);
                }
                unitPrice = offer.getAmount();         // 수락 제안한 금액
                priceSource = PriceSource.AGREED;      // 수락이라는 상태 값으로 로드.(PriceSource 은 EUME 객체로 LISTED 와 AGREED 두 가지 값만 가짐)
                offerId = offer.getOfferId();          // 수락 제안한 고유 식별 번호.
            }

            // 총 결제액 = 단가 × 수량. 저장 범위를 넘으면 VALIDATION_ERROR
            long paidAmount = AmountRules.totalAmount(unitPrice, quantity, "quantity");
            // 잔액 사전 검사. 지갑이 없으면 잔액 0 으로 본다
            long balance = walletService.currentBalance(buyerId);
            TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "잔액판단",
                    "구매자ID", buyerId, "결제금액", paidAmount, "잔액", balance,
                    "판단결과", balance < paidAmount ? "거절" : "통과");
            if (balance < paidAmount) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
            }
            // 겹침 재현 테스트의 중지 위치. 상품·예약·제안·지갑을 읽어 모든 검사를 통과한 직후, 주문을 저장하기 전이다. 테스트 설정이 꺼져 있거나 테스트 헤더가 없는 요청은 그대로 진행한다.
            testPause.reach(PausePoint.PAYMENT_CHECKED);

            // 주문 데이터들을 생성 하는 단계

            // 주문 내역 데이터 생성(테이블 "PURCHASE_ORDER") : 주문 생성(발송 대기, 진행 중). IDENTITY 채번이라 저장 시점에 INSERT 되어 주문 ID 가 정해진다
            PurchaseOrder order = purchaseOrderRepository.save(PurchaseOrder.place(product.getProductId(), buyerId,
                    product.getSellerId(), quantity, unitPrice, paidAmount, priceSource, offerId,
                    request.recipientName(), request.shippingAddress(), now, timeRules.shipDeadlineAt(now),
                    request.requestId()));
            TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "주문저장처리완료",
                    "주문ID", order.getOrderId(), "상품ID", product.getProductId(), "구매자ID", buyerId,
                    "주문수량", quantity, "결제금액", paidAmount);
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
            TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "차감처리완료",
                    "주문ID", order.getOrderId(), "주문수량", quantity,
                    "차감후남은수량", product.getRemainingQuantity(), "상품상태", product.getStatus());
            // 결제한 회원에게 걸린 활성 예약(만료 전)은 이 결제로 끝난다. 예약 수량보다 적게 결제해도 끝난다
            reservationService.fulfillOnPayment(product, buyerId, order.getOrderId(), now);
            // 이 상품에 대한 구매자의 대화가 없으면 시스템이 만든다
            conversationService.ensureTradeConversation(product, buyerId, now);

            logger.info("[결제 성공] orderId={}, memberId={}, productId={}, quantity={}, paidAmount={}, "
                            + "tradeStatus=null -> {}, shippingStatus=null -> {}",
                    order.getOrderId(), buyerId, product.getProductId(), quantity, paidAmount,
                    order.getTradeStatus(), order.getShippingStatus());

            // 상품의 모든 대화에 보낼 상태 이벤트를 등록한다. 실제 전송은 커밋 후에 한다
            conversationService.publishStateToProductConversations(product);
            // 잠금 대기 관찰용 중지 위치. 여기까지 실행된 UPDATE의 행 잠금을 유지한 채 커밋 직전에 멈춘다. flush를 호출하지 않으므로 SQL 실행 순서는 바꾸지 않는다.
            testPause.reach(PausePoint.PAYMENT_BEFORE_COMMIT);
            // 메서드가 반환되면 트랜잭션이 커밋된다
            TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "커밋전",
                    "주문ID", order.getOrderId());
            return order.getOrderId();
        } catch (RuntimeException e) {
            TradeTestLog.recordFailure(e);   // 업무 오류이면 오류 코드, 그 밖의 예외이면 예외 이름
            throw e;                         // 같은 예외를 그대로 다시 던짐
        }
    }
}
