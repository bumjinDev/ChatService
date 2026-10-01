package com.chatservice.marketplace.wallet;

import java.time.Instant;

public interface IWalletService {

	/** 요청한 회원 본인의 잔액을 충전하고 CHARGE 내역을 남긴다(F-003). */
	ChargeResponse charge(String memberId, ChargeRequest request);

	/**
	 * 회원 잔액을 늘리고 내역을 남긴다. 지갑이 없으면 잔액 0 으로 만든 뒤 더한다.
	 * 취소 반환, 환불 반환, 판매대금 지급에서 호출한다. 호출한 쪽의 트랜잭션에 참여한다.
	 */
	BalanceTransaction credit(String memberId, TransactionType type, long amount, Long orderId, String requestId,
			Instant now);
}
