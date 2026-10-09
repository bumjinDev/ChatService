package com.chatservice.marketplace.wallet.domain;

/**
 * 잔액 변동 내역의 유형. BALANCE_TRANSACTION.TYPE 컬럼에 문자열로 저장한다.
 *
 * 잔액을 바꿀 때마다 {@link Wallet#apply}가 이 유형으로 내역을 하나 기록한다.
 * 내역의 금액(AMOUNT)은 부호가 있으며, 잔액이 늘면 양수, 줄면 음수다.
 *
 * @see BalanceTransaction
 * @see Wallet
 */
public enum BalanceTransactionType {
    /** 충전. 금액은 양수이며 관련 주문이 없다. */
    CHARGE,
    /** 구매 결제. 구매자 잔액에서 결제 금액을 차감하며, 금액은 음수다. */
    PURCHASE,
    /** 주문 취소 반환. 당사자 취소 또는 미발송 자동 취소로 구매자에게 결제 금액을 반환하며, 금액은 양수다. */
    CANCEL_REFUND,
    /** 환불 반환. 판매자 환불 동의 또는 무응답 자동 환불로 구매자에게 결제 금액을 반환하며, 금액은 양수다. */
    REFUND,
    /** 판매대금 지급. 거래가 정상 완료되면 판매자에게 결제 금액을 지급하며, 금액은 양수다. */
    SALE_PAYOUT
}
