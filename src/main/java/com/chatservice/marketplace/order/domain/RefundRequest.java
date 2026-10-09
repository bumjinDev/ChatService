package com.chatservice.marketplace.order.domain;

import java.time.Instant;

import com.chatservice.marketplace.order.domain.OrderEnums.RefundDecision;
import com.chatservice.marketplace.order.domain.OrderEnums.RefundReasonCode;

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

/**
 * 주문당 하나인 유효한 환불 요청(REFUND_REQUEST). 수정·철회가 없고 최초 접수 시각을 바꾸지 않는다(F-017).
 * 판매자 동의·거절 또는 무응답 자동 승인으로 한 번만 판단된다(F-018, CH-001).
 */
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
    @Column(name = "REASON_CODE", nullable = false)
    private RefundReasonCode reasonCode;

    @Column(name = "DETAIL", nullable = false)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "DECISION", nullable = false)
    private RefundDecision decision;

    @Column(name = "REQUESTED_AT", nullable = false)
    private Instant requestedAt;

    @Column(name = "RESPONSE_DEADLINE_AT", nullable = false)
    private Instant responseDeadlineAt;

    @Column(name = "DECIDED_AT")
    private Instant decidedAt;

    @Column(name = "REQUEST_ID")
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

    /**
     * 처리 결과가 아직 기록되지 않은 요청인지 확인한다.
     *
     * @return 처리 결과가 응답 대기({@code PENDING})이면 {@code true}
     */
    public boolean isPending() {
        return decision == RefundDecision.PENDING;
    }

    /**
     * 환불 요청의 처리 결과와 처리 시각을 기록한다.
     *
     * 처리 결과는 {@code PENDING}에서 한 번만 바뀐다. 엔티티 필드만 바꾸며, UPDATE는 flush 때 dirty checking으로 실행된다.
     * 상태 검사는 현재 트랜잭션이 조회한 필드 값으로 한다. 조회 뒤에 다른 트랜잭션이 커밋한 변경은 검사에 반영되지 않는다.
     *
     * @param result 처리 결과. {@code APPROVED}, {@code REJECTED}, {@code AUTO_APPROVED} 중 하나
     * @param now    처리 시각(DECIDED_AT)
     * @throws IllegalStateException 이미 처리 결과가 기록된 요청이거나 {@code result}가 {@code PENDING}이면 던진다.
     */
    public void decide(RefundDecision result, Instant now) {
        if (!isPending() || result == RefundDecision.PENDING) {
            throw new IllegalStateException("이미 판단된 환불 요청입니다. refundRequestId=" + refundRequestId);
        }
        this.decision = result;
        this.decidedAt = now;
    }
}
