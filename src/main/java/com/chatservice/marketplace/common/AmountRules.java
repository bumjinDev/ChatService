package com.chatservice.marketplace.common;

import com.chatservice.marketplace.common.error.BusinessException;

/**
 * 금액·수량의 저장 범위(설계 4.3).
 *
 * 업무상 상한은 없지만 DB 컬럼(금액 NUMBER(15), 수량 NUMBER(10))에 들어가지 않는 값은
 * 자르거나 반올림하지 않고 변경 전에 거절한다. 충전·보유 상한 정책을 새로 두는 것이 아니다.
 */
public final class AmountRules {

    /** NUMBER(15) 에 저장할 수 있는 최대 금액(원). */
    public static final long MAX_AMOUNT = 999_999_999_999_999L;

    /** NUMBER(10) 에 저장할 수 있는 최대 수량. */
    public static final long MAX_QUANTITY = 9_999_999_999L;

    private AmountRules() {
    }

    /**
     * 개당 단가 × 수량. long 곱셈 범위를 넘거나 금액 저장 범위를 넘으면 400 으로 거절한다.
     */
    public static long totalAmount(long unitPrice, long quantity, String field) {
        long total;
        try {
            total = Math.multiplyExact(unitPrice, quantity);
        } catch (ArithmeticException overflow) {
            throw BusinessException.invalidField(field, "총 결제액이 저장 가능한 범위를 초과합니다.");
        }
        if (total > MAX_AMOUNT) {
            throw BusinessException.invalidField(field, "총 결제액이 저장 가능한 범위를 초과합니다.");
        }
        return total;
    }

    /** 잔액에 금액을 더한 결과가 저장 범위 안인지 확인한다. 두 값 모두 0 이상 MAX_AMOUNT 이하라서 long 범위는 넘지 않는다. */
    public static boolean fitsBalance(long balance, long increase) {
        return balance + increase <= MAX_AMOUNT;
    }
}
