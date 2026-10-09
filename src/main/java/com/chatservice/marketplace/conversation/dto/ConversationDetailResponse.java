package com.chatservice.marketplace.conversation.dto;

import java.time.Instant;
import java.util.List;

import com.chatservice.marketplace.common.PartyRole;
import com.chatservice.marketplace.conversation.domain.ConversationWritability.ReadOnlyReason;
import com.chatservice.marketplace.offer.domain.OfferStatus;
import com.chatservice.marketplace.offer.domain.PriceOffer;
import com.chatservice.marketplace.order.domain.OrderEnums.ShippingStatus;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.PurchaseOrder;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.domain.ProductCategory;
import com.chatservice.marketplace.product.domain.ProductStatus;

/**
 * 대화 상세·거래 표시(설계 5.2.8). 재접속 후 최신 저장 상태를 확인하는 용도다(F-007-AC-02).
 * orders 에는 이 대화의 상품·구매자·판매자가 모두 일치하는 주문만 담는다(BR-008).
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
        List<OrderSummary> orders) {

    public record ProductInfo(Long productId, String name, String description, ProductCategory category, long price,
                              long initialQuantity, long remainingQuantity, ProductStatus status) {

        public static ProductInfo of(Product product) {
            return new ProductInfo(product.getProductId(), product.getName(), product.getDescription(),
                    product.getCategory(), product.getPrice(), product.getInitialQuantity(),
                    product.getRemainingQuantity(), product.getStatus());
        }
    }

    public record Party(String nickname) {
    }

    public record OfferItem(Long offerId, long amount, OfferStatus status, Instant createdAt, Instant respondedAt) {

        public static OfferItem of(PriceOffer offer) {
            return new OfferItem(offer.getOfferId(), offer.getAmount(), offer.getStatus(), offer.getCreatedAt(),
                    offer.getRespondedAt());
        }
    }

    public record OrderSummary(Long orderId, long quantity, long unitPrice, long paidAmount, TradeStatus tradeStatus,
                               ShippingStatus shippingStatus) {

        public static OrderSummary of(PurchaseOrder order) {
            return new OrderSummary(order.getOrderId(), order.getQuantity(), order.getUnitPrice(),
                    order.getPaidAmount(), order.getTradeStatus(), order.getShippingStatus());
        }
    }
}
