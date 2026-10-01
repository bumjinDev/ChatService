package com.chatservice.marketplace.wallet;

import java.time.Instant;
import java.util.List;

public interface IWalletService {

	/** 요청한 회원 본인의 잔액을 충전하고 CHARGE 내역을 남긴다(F-003). */
	ChargeResponse charge(String memberId, ChargeRequest request);

	/** 요청한 회원 본인의 잔액. 지갑이 없으면 잔액 0 으로 만든다(F-004). */
	WalletResponse getMyWallet(String memberId);

	/** 요청한 회원 본인의 변동 내역을 최신순으로 돌려준다(F-004). */
	List<TransactionResponse> getMyTransactions(String memberId);

	/**
	 * 회원 잔액을 늘리고 내역을 남긴다. 지갑이 없으면 잔액 0 으로 만든 뒤 더한다.
	 * 취소 반환, 환불 반환, 판매대금 지급에서 호출한다. 호출한 쪽의 트랜잭션에 참여한다.
	 */
	BalanceTransaction credit(String memberId, TransactionType type, long amount, Long orderId, String requestId,
			Instant now);

	/** 현재 잔액. 지갑이 없으면 0 이며 지갑을 만들지 않는다. */
	long balanceOf(String memberId);

	/**
	 * 회원 잔액을 줄이고 내역을 남긴다. 잔액이 충분한지는 호출하는 쪽이 먼저 검사한다.
	 * 결제(PURCHASE)에서 호출한다. 호출한 쪽의 트랜잭션에 참여한다.
	 */
	BalanceTransaction debit(String memberId, TransactionType type, long amount, Long orderId, String requestId,
			Instant now);
}
