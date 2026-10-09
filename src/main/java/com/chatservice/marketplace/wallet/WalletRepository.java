package com.chatservice.marketplace.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatservice.marketplace.wallet.domain.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, String> {
}
