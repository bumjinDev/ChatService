package com.chatservice.marketplace.wallet;

/** 충전 결과: 변동 후 잔액과 충전 내역. */
public record ChargeResponse(long balance, TransactionResponse transaction) {
}
