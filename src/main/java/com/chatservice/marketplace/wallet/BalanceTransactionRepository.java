package com.chatservice.marketplace.wallet;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatservice.marketplace.wallet.domain.BalanceTransaction;

public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, Long> {

    /* 잔액 변동 내역 조회(F-004): 최신순 */
    List<BalanceTransaction> findByMemberIdOrderByTransactionIdDesc(String memberId);

    /* 주문 상세의 "본인의 반환 또는 판매대금 지급 결과"(F-019) */
    List<BalanceTransaction> findByMemberIdAndOrderIdOrderByTransactionIdAsc(String memberId, Long orderId);
}
