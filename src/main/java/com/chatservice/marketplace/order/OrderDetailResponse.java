package com.chatservice.marketplace.order;

import java.time.Instant;
import java.util.List;

import com.chatservice.marketplace.common.MemberRole;
import com.chatservice.marketplace.product.Category;
import com.chatservice.marketplace.wallet.TransactionResponse;

/**
 * 주문 상세 응답(설계 명세서 5.2.15). 결제, 발송 등록, 취소, 수령 확인, 환불 요청, 환불 응답의 성공 응답도 이 형식이다.
 * 수령인과 주소가 들어 있으므로 주문 당사자에게만 돌려준다.
 */
public record OrderDetailResponse(
		Long orderId,
		ProductView product,
		String buyerNickname,
		String sellerNickname,
		MemberRole myRole,
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
		ShipmentView shipment,
		Instant deliveredAt,
		Instant inspectionDeadlineAt,
		CancellationView cancellation,
		RefundRequestView refundRequest,
		Instant finalizedAt,
		List<TransactionResponse> myBalanceTransactions) {

	public record ProductView(Long productId, String name, String description, Category category, long price) {
	}

	public record ShipmentView(String carrierName, String trackingNumber, Instant shippedAt, Instant deliveryDueAt) {
	}

	public record CancellationView(CancelledBy cancelledBy, String reason, Instant cancelledAt) {
	}

	public record RefundRequestView(RefundReasonCode reasonCode, String detail, Instant requestedAt,
			Instant responseDeadlineAt, RefundDecision decision, Instant decidedAt) {
	}
}
