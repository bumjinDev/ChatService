package com.chatservice.marketplace.order.dto;

import java.time.Instant;

import com.chatservice.marketplace.order.domain.OrderEnums.PriceSource;
import com.chatservice.marketplace.order.domain.OrderEnums.ShippingStatus;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.PurchaseOrder;
import com.chatservice.marketplace.product.domain.Product;

/** 구매·판매 목록 항목(설계 5.2.14). 수령인·주소는 목록에 넣지 않는다. */
public record OrderSummaryResponse(
        Long orderId,
        ProductBrief product,
        long quantity,
        long unitPrice,
        long paidAmount,
        PriceSource priceSource,
        ShippingStatus shippingStatus,
        TradeStatus tradeStatus,
        Instant confirmedAt,
        Instant finalizedAt) {

    public record ProductBrief(Long productId, String name) {
    }

    public static OrderSummaryResponse of(PurchaseOrder order, Product product) {
        return new OrderSummaryResponse(order.getOrderId(), new ProductBrief(product.getProductId(), product.getName()),
                order.getQuantity(), order.getUnitPrice(), order.getPaidAmount(), order.getPriceSource(),
                order.getShippingStatus(), order.getTradeStatus(), order.getConfirmedAt(), order.getFinalizedAt());
    }
}
