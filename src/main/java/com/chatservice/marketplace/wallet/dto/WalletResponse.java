package com.chatservice.marketplace.wallet.dto;

/** 본인 잔액(설계 5.2.5). */
public record WalletResponse(String memberId, long balance) {
}
