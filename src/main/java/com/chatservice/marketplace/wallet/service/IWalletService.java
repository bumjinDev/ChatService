package com.chatservice.marketplace.wallet.service;

import java.time.Instant;
import java.util.List;

import com.chatservice.marketplace.wallet.domain.BalanceTransactionType;
import com.chatservice.marketplace.wallet.dto.BalanceTransactionResponse;
import com.chatservice.marketplace.wallet.dto.ChargeResponse;
import com.chatservice.marketplace.wallet.dto.WalletResponse;

/**
 * 잔액과 변동 내역을 한 곳에서 바꾼다(설계 3.2). 주문·취소·완료·환불 서비스는 자신의 트랜잭션 안에서
 * 아래 메서드를 호출해 잔액 변경과 내역 기록을 업무 결과와 함께 반영한다.
 */
public interface IWalletService {

    /** F-003 충전. 기본 구현은 호출마다 별도 충전으로 반영한다(같은 requestId 의 재시도 식별은 후속 과제). */
    ChargeResponse charge(String memberId, long amount, String requestId);

    /** F-004 본인 잔액. 지갑 행이 없으면 잔액 0 으로 만든다. */
    WalletResponse getWallet(String memberId);

    /** F-004 본인 변동 내역(최신순). */
    List<BalanceTransactionResponse> getTransactions(String memberId);

    /** 결제 전 잔액 확인용. 지갑이 없으면 0 이며 행을 만들지 않는다. */
    long currentBalance(String memberId);

    /** 결제 금액 차감과 PURCHASE 내역 기록. 잔액이 부족하면 INSUFFICIENT_BALANCE. */
    void debitForPurchase(String memberId, long amount, Long orderId, Instant now);

    /**
     * 반환·지급 전 잔액 저장 범위 확인. 범위를 넘으면 VALIDATION_ERROR(400)를 던진다.
     * 주문 상태를 바꾸기 전에 호출해서, 범위 초과 시 어떤 변경도 하지 않게 한다.
     */
    void ensureCreditable(String memberId, long amount);

    /** 반환(CANCEL_REFUND, REFUND) 또는 판매대금 지급(SALE_PAYOUT). 지갑이 없으면 만든다. */
    void credit(String memberId, long amount, BalanceTransactionType type, Long orderId, Instant now);

    /** 주문 상세의 본인 잔액 변동 내역. */
    List<BalanceTransactionResponse> transactionsForOrder(String memberId, Long orderId);
}
