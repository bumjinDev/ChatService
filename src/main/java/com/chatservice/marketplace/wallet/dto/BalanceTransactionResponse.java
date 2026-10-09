package com.chatservice.marketplace.wallet.dto;

import java.time.Instant;

import com.chatservice.marketplace.wallet.domain.BalanceTransaction;
import com.chatservice.marketplace.wallet.domain.BalanceTransactionType;

/** 잔액 변동 내역 항목(설계 5.2.5). orderId 가 관련 주문의 연결 정보이며 충전에는 없다. */
public record BalanceTransactionResponse(
        Long transactionId,
        BalanceTransactionType type,
        long amount,
        long balanceAfter,
        Long orderId,
        Instant createdAt) {

    public static BalanceTransactionResponse of(BalanceTransaction transaction) {
        return new BalanceTransactionResponse(transaction.getTransactionId(), transaction.getType(),
                transaction.getAmount(), transaction.getBalanceAfter(), transaction.getOrderId(),
                transaction.getCreatedAt());
    }
}
