package com.chatservice.marketplace.wallet;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, Long> {

	List<BalanceTransaction> findByMemberIdOrderByTransactionIdDesc(String memberId);

	List<BalanceTransaction> findByOrderIdAndMemberIdOrderByTransactionIdAsc(Long orderId, String memberId);

	List<BalanceTransaction> findByOrderIdOrderByTransactionIdAsc(Long orderId);
}
