package com.chatservice.marketplace.order.domain;

/**
 * 주문과 환불 요청에서 쓰는 열거형 모음.
 *
 * 각 값은 {@code @Enumerated(EnumType.STRING)}으로 PURCHASE_ORDER, REFUND_REQUEST 테이블의 컬럼에 문자열로 저장한다.
 *
 * @see PurchaseOrder
 * @see RefundRequest
 */
public final class OrderEnums {

    /** 열거형만 담는 클래스이므로 인스턴스를 만들지 않는다. */
    private OrderEnums() {
    }

    /**
     * 주문의 배송 진행 상태. PURCHASE_ORDER.SHIPPING_STATUS 컬럼.
     *
     * {@code WAITING_SHIPMENT → SHIPPING → DELIVERED} 순서로 한 방향으로만 바뀐다.
     * 발송 전에 취소된 주문은 {@code WAITING_SHIPMENT}로 남는다.
     */
    public enum ShippingStatus {
        /** 발송 대기. 결제 직후의 상태이며, 이 상태에서만 주문을 취소할 수 있다. */
        WAITING_SHIPMENT,
        /** 배송 중. 판매자가 발송 정보를 등록했다. */
        SHIPPING,
        /** 배송 완료. 모의 배송 기간이 지나 시스템이 바꾼 상태이며, 이때부터 상품 확인 기간 48시간이 시작된다. */
        DELIVERED
    }

    /**
     * 주문의 거래 결과 상태. PURCHASE_ORDER.TRADE_STATUS 컬럼.
     *
     * 허용하는 전환:
     *   - {@code IN_PROGRESS → CANCELLED}: 발송 전 당사자 취소, 미발송 자동 취소
     *   - {@code IN_PROGRESS → COMPLETED}: 구매 확정, 확인 기간 만료 자동 완료
     *   - {@code IN_PROGRESS → ON_HOLD}: 환불 요청
     *   - {@code ON_HOLD → REFUNDED}: 판매자 환불 동의, 무응답 자동 환불
     *   - {@code ON_HOLD → COMPLETED}: 판매자 환불 거절
     * {@code COMPLETED}, {@code REFUNDED}, {@code CANCELLED}는 최종 상태이며 서로 배타적이다.
     *
     * @see PurchaseOrder
     */
    public enum TradeStatus {
        /** 진행 중. 결제부터 거래가 끝나기 전까지의 상태다. */
        IN_PROGRESS,
        /** 보류. 환불 요청이 접수되어 판매자 응답이나 자동 환불을 기다린다. 구매 확정과 자동 완료를 할 수 없다. */
        ON_HOLD,
        /** 정상 완료(최종 상태). 판매자에게 판매대금을 지급했다. */
        COMPLETED,
        /** 환불 완료(최종 상태). 구매자에게 결제 금액 전액을 반환했다. */
        REFUNDED,
        /** 취소 완료(최종 상태). 발송 전에 취소되어 구매자에게 결제 금액 전액을 반환했다. */
        CANCELLED;

        /**
         * 최종 상태인지 확인한다.
         *
         * @return {@code COMPLETED}, {@code REFUNDED}, {@code CANCELLED} 중 하나이면 {@code true}
         */
        public boolean isFinal() {
            return this == COMPLETED || this == REFUNDED || this == CANCELLED;
        }
    }

    /** 주문에 적용한 단가의 출처. PURCHASE_ORDER.PRICE_SOURCE 컬럼. */
    public enum PriceSource {
        /** 상품의 등록 가격을 적용했다. */
        LISTED,
        /** 판매자가 수락한 가격 제안의 금액을 적용했다. 주문의 OFFER_ID에 제안 ID가 저장된다. */
        AGREED
    }

    /** 거래가 정상 완료된 이유. PURCHASE_ORDER.COMPLETION_CAUSE 컬럼이며, 정상 완료가 아닌 주문은 null이다. */
    public enum CompletionCause {
        /** 구매자가 상품 확인 기간 안에 구매를 확정했다. */
        BUYER_CONFIRMED,
        /** 상품 확인 기간이 지나 시스템이 자동으로 완료했다. */
        AUTO_EXPIRED,
        /** 판매자가 환불 요청을 거절했다. 보류(ON_HOLD)에서 정상 완료로 바뀌는 유일한 경우다. */
        REFUND_REJECTED
    }

    /** 주문을 취소한 주체. PURCHASE_ORDER.CANCELLED_BY 컬럼이며, 취소되지 않은 주문은 null이다. */
    public enum CancelledBy {
        /** 구매자가 발송 전에 취소했다. */
        BUYER,
        /** 판매자가 발송 전에 취소했다. */
        SELLER,
        /** 발송 기한까지 발송 정보가 등록되지 않아 시스템이 자동으로 취소했다. 취소 사유는 SHIPMENT_DEADLINE_EXPIRED로 저장된다. */
        SYSTEM
    }

    /** 환불 요청 사유. REFUND_REQUEST.REASON_CODE 컬럼. 현재 범위에서는 한 가지 사유만 허용한다. */
    public enum RefundReasonCode {
        /** 상품 설명과 실제 상태가 다르다. */
        DESCRIPTION_MISMATCH
    }

    /**
     * 환불 요청의 처리 결과. REFUND_REQUEST.DECISION 컬럼.
     *
     * {@code PENDING}에서 나머지 세 값 중 하나로 한 번만 바뀐다.
     *
     * @see RefundRequest
     */
    public enum RefundDecision {
        /** 판매자 응답 대기. 주문은 보류(ON_HOLD) 상태다. */
        PENDING,
        /** 판매자가 환불에 동의했다. 주문은 환불 완료(REFUNDED)가 된다. */
        APPROVED,
        /** 판매자가 환불을 거절했다. 주문은 정상 완료(COMPLETED)가 되고 판매대금을 지급한다. */
        REJECTED,
        /** 판매자가 응답 기한(접수 후 48시간)까지 응답하지 않아 시스템이 자동으로 승인했다. 주문은 환불 완료(REFUNDED)가 된다. */
        AUTO_APPROVED
    }
}
