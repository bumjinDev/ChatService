package com.chatservice.marketplace.conversation.dto;

import java.time.Instant;
import java.util.List;

import com.chatservice.marketplace.common.PartyRole;
import com.chatservice.marketplace.conversation.domain.ConversationWritability.ReadOnlyReason;
import com.chatservice.marketplace.offer.domain.AgreementStatus;
import com.chatservice.marketplace.offer.domain.OfferStatus;
import com.chatservice.marketplace.offer.domain.PriceOffer;
import com.chatservice.marketplace.order.domain.OrderEnums.PriceSource;
import com.chatservice.marketplace.order.domain.OrderEnums.ShippingStatus;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.PurchaseOrder;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.domain.ProductCategory;
import com.chatservice.marketplace.product.domain.ProductStatus;
import com.chatservice.marketplace.reservation.dto.ReservationResponse;

/**
 * 대화 상세·거래 표시(설계 5.2.8). 재접속 후 최신 저장 상태를 확인하는 용도다(F-007-AC-02).
 * orders 에는 이 대화의 상품·구매자·판매자가 모두 일치하는 주문만 담는다(BR-008).
 * reservation 은 이 대화의 상품·구매 희망자로 저장된 예약 가운데 가장 최근 것 하나이며, 없으면 null 이다.
 */
public record ConversationDetailResponse(
        Long conversationId,
        ProductInfo product,
        Party buyer,
        Party seller,
        PartyRole myRole,
        boolean writable,
        ReadOnlyReason readOnlyReason,
        List<OfferItem> offers,
        List<OrderSummary> orders,
        ReservationResponse reservation) {

    /** availableQuantity 는 조회한 회원 기준 구매 가능 수량이다. 남은 수량에서 다른 회원에게 걸린 활성 예약의 수량을 뺀 값이다. */
    public record ProductInfo(Long productId, String name, String description, ProductCategory category, long price,
                              long initialQuantity, long remainingQuantity, long availableQuantity, ProductStatus status) {

        public static ProductInfo of(Product product, long availableQuantity) {
            return new ProductInfo(product.getProductId(), product.getName(), product.getDescription(),
                    product.getCategory(), product.getPrice(), product.getInitialQuantity(),
                    product.getRemainingQuantity(), availableQuantity, product.getStatus());
        }
    }

    public record Party(String nickname) {
    }

    /** quantity 는 제안 수량이다. agreementStatus 는 수락된 제안의 합의 상태이며, 응답 대기 제안과 거절된 제안에서는 null 이다. */
    public record OfferItem(Long offerId, long amount, long quantity, OfferStatus status, AgreementStatus agreementStatus,
                            Instant createdAt, Instant respondedAt) {

        public static OfferItem of(PriceOffer offer, AgreementStatus agreementStatus) {
            return new OfferItem(offer.getOfferId(), offer.getAmount(), offer.getQuantity(), offer.getStatus(),
                    agreementStatus, offer.getCreatedAt(), offer.getRespondedAt());
        }
    }

    /** priceSource 는 적용 단가의 출처이고, offerId 는 합의 가격 주문일 때 쓴 제안 ID 이며 등록 가격 주문이면 null 이다. */
    public record OrderSummary(Long orderId, long quantity, long unitPrice, long paidAmount, PriceSource priceSource,
                               Long offerId, TradeStatus tradeStatus, ShippingStatus shippingStatus) {

        public static OrderSummary of(PurchaseOrder order) {
            return new OrderSummary(order.getOrderId(), order.getQuantity(), order.getUnitPrice(),
                    order.getPaidAmount(), order.getPriceSource(), order.getOfferId(), order.getTradeStatus(),
                    order.getShippingStatus());
        }
    }
}
