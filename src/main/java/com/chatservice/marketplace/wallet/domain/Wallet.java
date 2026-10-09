package com.chatservice.marketplace.wallet.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 테스트 잔액(WALLET). 회원 ID 를 그대로 PK 로 쓴다.
 * 잔액 변경은 WalletService 를 통해서만 하며, 변경할 때마다 BalanceTransaction 을 함께 기록한다(BR-003).
 */
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

    /**
     * 잔액 0인 새 지갑 객체를 만든다. 저장은 호출하는 쪽이 한다.
     *
     * @param memberId 회원 ID
     * @param now      UPDATED_AT
     * @return 새 지갑 객체
     */
    public static Wallet open(String memberId, Instant now) {
        Wallet wallet = new Wallet();
        wallet.memberId = memberId;
        wallet.balance = 0;
        wallet.updatedAt = now;
        return wallet;
    }

    /**
     * 잔액을 늘리고 UPDATED_AT을 갱신한다. 외부에서는 {@link #apply}를 통해서만 호출한다.
     *
     * @param amount 늘릴 금액(양수)
     * @param now    UPDATED_AT
     */
    void increase(long amount, Instant now) {
        balance += amount;
        updatedAt = now;
    }

    void decrease(long amount, Instant now) {
        if (amount > balance) {
            throw new IllegalStateException("잔액보다 큰 금액을 차감할 수 없습니다. memberId=" + memberId);
        }
        balance -= amount;
        updatedAt = now;
    }

    /**
     * 잔액을 바꾸고, 바뀐 잔액을 담은 잔액 변동 내역 객체를 만든다.
     *
     * 내역 객체의 저장은 호출하는 쪽({@code WalletService})이 한다.
     *
     * @param type      내역 유형
     * @param amount    부호 있는 변동량. 양수면 {@link #increase}, 0 이하면 {@link #decrease}를 호출한다.
     * @param orderId   관련 주문 ID. 충전은 null
     * @param requestId 클라이언트 요청 식별자. 충전 외에는 null
     * @param now       변경 시각
     * @return 변동 후 잔액(balanceAfter)을 담은 내역 객체
     */
    public BalanceTransaction apply(BalanceTransactionType type, long amount, Long orderId, String requestId, Instant now) {
        if (amount > 0) {
            increase(amount, now);
        } else {
            decrease(-amount, now);
        }
        return BalanceTransaction.record(memberId, type, amount, balance, orderId, requestId, now);
    }
}
