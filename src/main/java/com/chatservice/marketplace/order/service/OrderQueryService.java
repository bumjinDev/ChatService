package com.chatservice.marketplace.order.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.PartyRole;
import com.chatservice.marketplace.common.error.BusinessException;
import com.chatservice.marketplace.common.error.ErrorCode;
import com.chatservice.marketplace.common.member.MemberDirectory;
import com.chatservice.marketplace.order.PurchaseOrderRepository;
import com.chatservice.marketplace.order.RefundRequestRepository;
import com.chatservice.marketplace.order.ShipmentRepository;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.PurchaseOrder;
import com.chatservice.marketplace.order.dto.OrderDetailResponse;
import com.chatservice.marketplace.order.dto.OrderSummaryResponse;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.service.IProductService;
import com.chatservice.marketplace.wallet.service.IWalletService;

/** 주문 조회(F-019). 조회는 어떤 상태도 바꾸지 않는다. */
@Service
public class OrderQueryService implements IOrderQueryService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ShipmentRepository shipmentRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final IProductService productService;
    private final IWalletService walletService;
    private final MemberDirectory memberDirectory;

    public OrderQueryService(PurchaseOrderRepository purchaseOrderRepository, ShipmentRepository shipmentRepository,
                             RefundRequestRepository refundRequestRepository, IProductService productService,
                             IWalletService walletService, MemberDirectory memberDirectory) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.shipmentRepository = shipmentRepository;
        this.refundRequestRepository = refundRequestRepository;
        this.productService = productService;
        this.walletService = walletService;
        this.memberDirectory = memberDirectory;
    }

    /*
     * 수령인·주소를 포함하므로 당사자 검사를 통과한 경우에만 조립한다(BR-008, NFR-003).
     * myBalanceTransactions 는 이 주문에 연결된 요청자 본인의 내역만 담는다(다른 당사자의 지급·반환 내역은 넣지 않는다).
     */
    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(String memberId, Long orderId) {
        PurchaseOrder order = purchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isParty(memberId)) {
            throw new BusinessException(ErrorCode.NOT_TRADE_PARTY);
        }
        Product product = productService.getProduct(order.getProductId());
        Map<String, String> nicknames = memberDirectory.nicknames(Set.of(order.getBuyerId(), order.getSellerId()));
        OrderDetailResponse.CancellationInfo cancellation = order.getTradeStatus() == TradeStatus.CANCELLED
                ? new OrderDetailResponse.CancellationInfo(order.getCancelledBy(), order.getCancelReason(), order.getFinalizedAt())
                : null;
        return new OrderDetailResponse(
                order.getOrderId(),
                OrderDetailResponse.ProductInfo.of(product),
                nicknames.get(order.getBuyerId()),
                nicknames.get(order.getSellerId()),
                order.isBuyer(memberId) ? PartyRole.BUYER : PartyRole.SELLER,
                order.getQuantity(),
                order.getUnitPrice(),
                order.getPaidAmount(),
                order.getPriceSource(),
                order.getOfferId(),
                order.getRecipientName(),
                order.getShippingAddress(),
                order.getShippingStatus(),
                order.getTradeStatus(),
                order.getCompletionCause(),
                order.getConfirmedAt(),
                order.getShipDeadlineAt(),
                shipmentRepository.findByOrderId(orderId).map(OrderDetailResponse.ShipmentInfo::of).orElse(null),
                order.getDeliveredAt(),
                order.getInspectionDeadlineAt(),
                cancellation,
                refundRequestRepository.findByOrderId(orderId).map(OrderDetailResponse.RefundRequestInfo::of).orElse(null),
                order.getFinalizedAt(),
                walletService.transactionsForOrder(memberId, orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> purchases(String memberId) {
        return summaries(purchaseOrderRepository.findByBuyerIdOrderByConfirmedAtDescOrderIdDesc(memberId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> sales(String memberId) {
        return summaries(purchaseOrderRepository.findBySellerIdOrderByConfirmedAtDescOrderIdDesc(memberId));
    }

    private List<OrderSummaryResponse> summaries(List<PurchaseOrder> orders) {
        Map<Long, Product> products = new HashMap<>();
        return orders.stream()
                .map(order -> OrderSummaryResponse.of(order,
                        products.computeIfAbsent(order.getProductId(), productService::getProduct)))
                .toList();
    }
}
