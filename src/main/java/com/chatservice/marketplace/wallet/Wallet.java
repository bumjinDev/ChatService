package com.chatservice.marketplace.wallet;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** WALLET 테이블. 회원 ID 를 PK 로 쓰는 회원별 현재 잔액이다. */
@Entity
@Table(name = "WALLET")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

	@Id
	@Column(name = "MEMBER_ID")
	private String memberId;

	@Column(name = "BALANCE", nullable = false)
	private long balance;

	@Column(name = "UPDATED_AT", nullable = false)
	private Instant updatedAt;

	public static Wallet open(String memberId, Instant now) {
		Wallet wallet = new Wallet();
		wallet.memberId = memberId;
		wallet.balance = 0L;
		wallet.updatedAt = now;
		return wallet;
	}

	/** 잔액을 늘리고 변동 후 잔액을 돌려준다. */
	public long deposit(long amount, Instant now) {
		this.balance += amount;
		this.updatedAt = now;
		return this.balance;
	}

	/** 잔액을 줄이고 변동 후 잔액을 돌려준다. 잔액 충분 여부는 호출하는 쪽이 먼저 검사한다. */
	public long withdraw(long amount, Instant now) {
		this.balance -= amount;
		this.updatedAt = now;
		return this.balance;
	}
}
