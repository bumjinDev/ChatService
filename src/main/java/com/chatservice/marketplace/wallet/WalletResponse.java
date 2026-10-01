package com.chatservice.marketplace.wallet;

/** 잔액 조회 응답(설계 명세서 5.2.5). */
public record WalletResponse(String memberId, long balance) {
}
