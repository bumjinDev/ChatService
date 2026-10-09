package com.chatservice.marketplace.order.dto;

import java.time.Instant;
import java.util.List;

import com.chatservice.marketplace.common.PartyRole;
import com.chatservice.marketplace.order.domain.OrderEnums.CancelledBy;
import com.chatservice.marketplace.order.domain.OrderEnums.CompletionCause;
import com.chatservice.marketplace.order.domain.OrderEnums.PriceSource;
import com.chatservice.marketplace.order.domain.OrderEnums.RefundDecision;
import com.chatservice.marketplace.order.domain.OrderEnums.RefundReasonCode;
import com.chatservice.marketplace.order.domain.OrderEnums.ShippingStatus;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.RefundRequest;
import com.chatservice.marketplace.order.domain.Shipment;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.domain.ProductCategory;
import com.chatservice.marketplace.wallet.dto.BalanceTransactionResponse;

/**
 * 주문 상세·결제·배송·최종 금전 결과(설계 5.2.15). 결제·발송·취소·수령 확인·환불 요청·환불 응답의 성공 응답도
 * 같은 형식이다. 수령인·주소를 담으므로 주문 당사자에게만 돌려준다.
 */
public record OrderDetailResponse(
        Long orderId,
        ProductInfo product,
        String buyerNickname,
        String sellerNickname,
        PartyRole myRole,
        long quantity,
        long unitPrice,
        long paidAmount,
        PriceSource priceSource,
        Long offerId,
        String recipientName,
        String shippingAddress,
        ShippingStatus shippingStatus,
        TradeStatus tradeStatus,
        CompletionCause completionCause,
        Instant confirmedAt,
        Instant shipDeadlineAt,
        ShipmentInfo shipment,
        Instant deliveredAt,
        Instant inspectionDeadlineAt,
        CancellationInfo cancellation,
        RefundRequestInfo refundRequest,
        Instant finalizedAt,
        List<BalanceTransactionResponse> myBalanceTransactions) {

    public record ProductInfo(Long productId, String name, String description, ProductCategory category, long price) {

        public static ProductInfo of(Product product) {
            return new ProductInfo(product.getProductId(), product.getName(), product.getDescription(),
                    product.getCategory(), product.getPrice());
        }
    }

    public record ShipmentInfo(String carrierName, String trackingNumber, Instant shippedAt, Instant deliveryDueAt) {

        public static ShipmentInfo of(Shipment shipment) {
            return new ShipmentInfo(shipment.getCarrierName(), shipment.getTrackingNumber(), shipment.getShippedAt(),
                    shipment.getDeliveryDueAt());
        }
    }

    public record CancellationInfo(CancelledBy cancelledBy, String reason, Instant cancelledAt) {
    }

    public record RefundRequestInfo(RefundReasonCode reasonCode, String detail, Instant requestedAt,
                                    Instant responseDeadlineAt, RefundDecision decision, Instant decidedAt) {

        public static RefundRequestInfo of(RefundRequest request) {
            return new RefundRequestInfo(request.getReasonCode(), request.getDetail(), request.getRequestedAt(),
                    request.getResponseDeadlineAt(), request.getDecision(), request.getDecidedAt());
        }
    }
}
