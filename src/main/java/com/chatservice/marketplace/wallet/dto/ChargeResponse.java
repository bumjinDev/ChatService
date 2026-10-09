package com.chatservice.marketplace.wallet.dto;

/** 충전 결과(설계 5.2.4): 변동 후 잔액과 기록된 충전 내역. */
public record ChargeResponse(long balance, BalanceTransactionResponse transaction) {
}
