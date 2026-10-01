package com.chatservice.marketplace.wallet;

import java.time.Instant;

/** 잔액 변동 내역 한 건(설계 명세서 5.2.4, 5.2.5). */
public record TransactionResponse(
		Long transactionId,
		TransactionType type,
		long amount,
		long balanceAfter,
		Long orderId,
		Instant createdAt) {

	public static TransactionResponse of(BalanceTransaction tx) {
		return new TransactionResponse(tx.getTransactionId(), tx.getType(), tx.getAmount(), tx.getBalanceAfter(),
				tx.getOrderId(), tx.getCreatedAt());
	}
}
