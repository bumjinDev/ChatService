package com.chatservice.marketplace.order;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.common.MemberDirectory;
import com.chatservice.marketplace.order.OrderDetailResponse.CancellationView;
import com.chatservice.marketplace.order.OrderDetailResponse.ProductView;
import com.chatservice.marketplace.order.OrderDetailResponse.RefundRequestView;
import com.chatservice.marketplace.order.OrderDetailResponse.ShipmentView;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductRepository;
import com.chatservice.marketplace.wallet.BalanceTransactionRepository;
import com.chatservice.marketplace.wallet.TransactionResponse;

/**
 * 주문 상세 응답을 조립한다. 주문, 상품 요약, 두 회원의 닉네임, 발송, 취소, 환불 요청과
 * 이 주문에 연결된 요청한 회원 본인의 잔액 변동 내역을 붙인다(설계 명세서 6.19절).
 */
@Component
public class OrderDetailAssembler {

	private final ProductRepository productRepository;
	private final ShipmentRepository shipmentRepository;
	private final RefundRequestRepository refundRequestRepository;
	private final BalanceTransactionRepository transactionRepository;
	private final MemberDirectory memberDirectory;

	public OrderDetailAssembler(ProductRepository productRepository, ShipmentRepository shipmentRepository,
			RefundRequestRepository refundRequestRepository, BalanceTransactionRepository transactionRepository,
			MemberDirectory memberDirectory) {
		this.productRepository = productRepository;
		this.shipmentRepository = shipmentRepository;
		this.refundRequestRepository = refundRequestRepository;
		this.transactionRepository = transactionRepository;
		this.memberDirectory = memberDirectory;
	}

	public OrderDetailResponse detail(PurchaseOrder order, String memberId) {
		Product product = productRepository.findById(order.getProductId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		Map<String, String> nicknames = memberDirectory.nicknames(List.of(order.getBuyerId(), order.getSellerId()));
		ShipmentView shipment = shipmentRepository.findFirstByOrderIdOrderByShipmentIdAsc(order.getOrderId())
				.map(s -> new ShipmentView(s.getCarrierName(), s.getTrackingNumber(), s.getShippedAt(),
						s.getDeliveryDueAt()))
				.orElse(null);
		CancellationView cancellation = order.getTradeStatus() == TradeStatus.CANCELLED
				? new CancellationView(order.getCancelledBy(), order.getCancelReason(), order.getFinalizedAt())
				: null;
		RefundRequestView refund = refundRequestRepository
				.findFirstByOrderIdOrderByRefundRequestIdAsc(order.getOrderId())
				.map(r -> new RefundRequestView(r.getReasonCode(), r.getDetail(), r.getRequestedAt(),
						r.getResponseDeadlineAt(), r.getDecision(), r.getDecidedAt()))
				.orElse(null);
		List<TransactionResponse> myTransactions = transactionRepository
				.findByOrderIdAndMemberIdOrderByTransactionIdAsc(order.getOrderId(), memberId).stream()
				.map(TransactionResponse::of)
				.toList();
		return new OrderDetailResponse(
				order.getOrderId(),
				new ProductView(product.getProductId(), product.getName(), product.getDescription(),
						product.getCategory(), product.getPrice()),
				nicknames.get(order.getBuyerId()),
				nicknames.get(order.getSellerId()),
				order.roleOf(memberId),
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
				shipment,
				order.getDeliveredAt(),
				order.getInspectionDeadlineAt(),
				cancellation,
				refund,
				order.getFinalizedAt(),
				myTransactions);
	}
}
