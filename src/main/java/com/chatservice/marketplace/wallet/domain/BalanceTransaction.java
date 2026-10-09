package com.chatservice.marketplace.wallet.domain;

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

/** 잔액 변동 내역(BALANCE_TRANSACTION). 기록 후 수정·삭제하지 않는다. */
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
    @Column(name = "TYPE", nullable = false)
    private BalanceTransactionType type;

    /** 부호 있는 변동량. 잔액이 늘면 양수, 줄면 음수. */
    @Column(name = "AMOUNT", nullable = false)
    private long amount;

    @Column(name = "BALANCE_AFTER", nullable = false)
    private long balanceAfter;

    @Column(name = "ORDER_ID")
    private Long orderId;

    /** 클라이언트가 보낸 요청 식별자. 기본 구현은 이 값으로 중복 요청을 판단하지 않는다(후속 과제). */
    @Column(name = "REQUEST_ID")
    private String requestId;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    /**
     * 잔액 변동 내역 객체를 만든다. {@link Wallet#apply}에서만 호출한다.
     *
     * @param memberId     잔액의 주인
     * @param type         내역 유형
     * @param amount       부호 있는 변동량
     * @param balanceAfter 변동 후 잔액
     * @param orderId      관련 주문 ID. 충전은 null
     * @param requestId    클라이언트 요청 식별자
     * @param now          발생 시각(CREATED_AT)
     * @return 저장 전 내역 객체
     */
    static BalanceTransaction record(String memberId, BalanceTransactionType type, long amount, long balanceAfter,
                                     Long orderId, String requestId, Instant now) {
        BalanceTransaction transaction = new BalanceTransaction();
        transaction.memberId = memberId;
        transaction.type = type;
        transaction.amount = amount;
        transaction.balanceAfter = balanceAfter;
        transaction.orderId = orderId;
        transaction.requestId = requestId;
        transaction.createdAt = now;
        return transaction;
    }
}
