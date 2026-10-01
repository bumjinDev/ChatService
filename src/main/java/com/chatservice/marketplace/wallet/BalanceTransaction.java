package com.chatservice.marketplace.wallet;

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

/** BALANCE_TRANSACTION 테이블. 잔액 변동 한 건과 변동 후 잔액을 기록한다. 삭제하지 않는다. */
@Entity
@Table(name = "BALANCE_TRANSACTION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "TRANSACTION_ID")
	private Long transactionId;

	@Column(name = "MEMBER_ID", nullable = false)
	private String memberId;

	@Enumerated(EnumType.STRING)
	@Column(name = "TYPE", nullable = false, length = 30)
	private TransactionType type;

	/** 부호 있는 변동량. 잔액이 늘면 양수, 줄면 음수다. */
	@Column(name = "AMOUNT", nullable = false)
	private long amount;

	@Column(name = "BALANCE_AFTER", nullable = false)
	private long balanceAfter;

	@Column(name = "ORDER_ID")
	private Long orderId;

	@Column(name = "REQUEST_ID", length = 64)
	private String requestId;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	public static BalanceTransaction record(String memberId, TransactionType type, long amount, long balanceAfter,
			Long orderId, String requestId, Instant now) {
		BalanceTransaction tx = new BalanceTransaction();
		tx.memberId = memberId;
		tx.type = type;
		tx.amount = amount;
		tx.balanceAfter = balanceAfter;
		tx.orderId = orderId;
		tx.requestId = requestId;
		tx.createdAt = now;
		return tx;
	}
}
