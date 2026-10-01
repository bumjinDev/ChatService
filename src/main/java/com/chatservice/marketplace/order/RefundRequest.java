package com.chatservice.marketplace.order;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** REFUND_REQUEST 테이블. 주문당 하나인 환불 요청이다. 수정과 철회는 없다. */
@Entity
@Table(name = "REFUND_REQUEST")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "REFUND_REQUEST_ID")
	private Long refundRequestId;

	@Column(name = "ORDER_ID", nullable = false)
	private Long orderId;

	@Column(name = "BUYER_ID", nullable = false)
	private String buyerId;

	@Enumerated(EnumType.STRING)
	@Column(name = "REASON_CODE", nullable = false, length = 30)
	private RefundReasonCode reasonCode;

	@Column(name = "DETAIL", nullable = false, length = 2000)
	private String detail;

	@Enumerated(EnumType.STRING)
	@Column(name = "DECISION", nullable = false, length = 30)
	private RefundDecision decision;

	@Column(name = "REQUESTED_AT", nullable = false)
	private Instant requestedAt;

	@Column(name = "RESPONSE_DEADLINE_AT", nullable = false)
	private Instant responseDeadlineAt;

	@Column(name = "DECIDED_AT")
	private Instant decidedAt;

	@Column(name = "REQUEST_ID", length = 64)
	private String requestId;

	public static RefundRequest submit(Long orderId, String buyerId, RefundReasonCode reasonCode, String detail,
			Instant requestedAt, Instant responseDeadlineAt, String requestId) {
		RefundRequest request = new RefundRequest();
		request.orderId = orderId;
		request.buyerId = buyerId;
		request.reasonCode = reasonCode;
		request.detail = detail;
		request.decision = RefundDecision.PENDING;
		request.requestedAt = requestedAt;
		request.responseDeadlineAt = responseDeadlineAt;
		request.requestId = requestId;
		return request;
	}

	public void decide(RefundDecision decision, Instant now) {
		this.decision = decision;
		this.decidedAt = now;
	}
}
