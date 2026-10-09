package com.chatservice.marketplace.wallet.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.AmountRules;
import com.chatservice.marketplace.common.error.BusinessException;
import com.chatservice.marketplace.common.error.ErrorCode;
import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.wallet.BalanceTransactionRepository;
import com.chatservice.marketplace.wallet.WalletRepository;
import com.chatservice.marketplace.wallet.domain.BalanceTransaction;
import com.chatservice.marketplace.wallet.domain.BalanceTransactionType;
import com.chatservice.marketplace.wallet.domain.Wallet;
import com.chatservice.marketplace.wallet.dto.BalanceTransactionResponse;
import com.chatservice.marketplace.wallet.dto.ChargeResponse;
import com.chatservice.marketplace.wallet.dto.WalletResponse;

@Service
public class WalletService implements IWalletService {

    private static final Logger logger = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;
    private final TimeRules timeRules;

    public WalletService(WalletRepository walletRepository,
                         BalanceTransactionRepository balanceTransactionRepository,
                         TimeRules timeRules) {
        this.walletRepository = walletRepository;
        this.balanceTransactionRepository = balanceTransactionRepository;
        this.timeRules = timeRules;
    }

    /* 잔액 증가와 충전 내역 기록을 한 트랜잭션으로 반영한다(설계 6.3 "함께 반영할 변경"). */
    @Override
    @Transactional
    public ChargeResponse charge(String memberId, long amount, String requestId) {
        Instant now = timeRules.now();
        Wallet wallet = getOrOpen(memberId, now);
        if (!AmountRules.fitsBalance(wallet.getBalance(), amount)) {
            throw BusinessException.invalidField("amount", "충전 후 잔액이 저장 가능한 범위를 초과합니다.");
        }
        BalanceTransaction transaction = balanceTransactionRepository.save(
                wallet.apply(BalanceTransactionType.CHARGE, amount, null, requestId, now));
        logger.info("[충전] memberId={}, amount={}, balanceAfter={}", memberId, amount, wallet.getBalance());
        return new ChargeResponse(wallet.getBalance(), BalanceTransactionResponse.of(transaction));
    }

    @Override
    @Transactional
    public WalletResponse getWallet(String memberId) {
        Wallet wallet = getOrOpen(memberId, timeRules.now());
        return new WalletResponse(memberId, wallet.getBalance());
    }

    @Override
    @Transactional
    public List<BalanceTransactionResponse> getTransactions(String memberId) {
        getOrOpen(memberId, timeRules.now());
        return balanceTransactionRepository.findByMemberIdOrderByTransactionIdDesc(memberId).stream()
                .map(BalanceTransactionResponse::of)
                .toList();
    }

    /**
     * 회원의 현재 잔액을 반환한다. 지갑 행이 없으면 행을 만들지 않고 0을 반환한다.
     *
     * @param memberId 회원 ID
     * @return 현재 잔액. 지갑 행이 없으면 0
     */
    @Override
    @Transactional(readOnly = true)
    public long currentBalance(String memberId) {
        return walletRepository.findById(memberId).map(Wallet::getBalance).orElse(0L);
    }

    @Override
    @Transactional
    public void debitForPurchase(String memberId, long amount, Long orderId, Instant now) {
        Wallet wallet = walletRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSUFFICIENT_BALANCE));
        if (wallet.getBalance() < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        balanceTransactionRepository.save(
                wallet.apply(BalanceTransactionType.PURCHASE, -amount, orderId, null, now));
        logger.info("[구매 차감] memberId={}, orderId={}, amount={}, balanceAfter={}",
                memberId, orderId, amount, wallet.getBalance());
    }

    /**
     * 잔액에 금액을 더해도 저장 범위(NUMBER(15))를 넘지 않는지 확인한다.
     *
     * 취소 반환, 환불 반환, 판매대금 지급에서 주문 상태를 바꾸기 전에 호출한다.
     * 호출하는 쪽 트랜잭션에 참여해 실행하며, 여기서 조회한 지갑 엔티티는 그 트랜잭션의 영속성 컨텍스트에 남는다.
     *
     * [검토 메모] 반환·지급 후 잔액이 금액 저장 상한(NUMBER(15))을 넘으면 400으로 거절하고 아무것도 반영하지 않는다.
     * 이때 취소·환불·판매대금 지급이 실행되지 않아 거래를 끝낼 수 없다. 자동 처리는 실행할 때마다 실패 로그만 남기고 같은 건을 다시 대상으로 잡는다.
     * 이 상황의 업무 처리 방식은 기획·요구사항에 정해져 있지 않다.
     *
     * @param memberId 잔액을 받을 회원 ID
     * @param amount   더할 금액
     * @throws BusinessException 저장 범위를 넘으면 {@code VALIDATION_ERROR}
     */
    @Override
    @Transactional(readOnly = true)
    public void ensureCreditable(String memberId, long amount) {
        long balance = currentBalance(memberId);
        if (!AmountRules.fitsBalance(balance, amount)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "반환·지급 후 잔액이 저장 가능한 범위를 초과합니다.");
        }
    }

    /**
     * 회원 잔액을 늘리고 잔액 변동 내역을 기록한다. 취소 반환, 환불 반환, 판매대금 지급이 사용한다.
     *
     * 처리 흐름: 지갑 조회(없으면 생성) → 저장 범위 확인 → {@link Wallet#apply}로 잔액 증가와 내역 객체 생성 → 내역 INSERT.
     * 잔액은 Java에서 계산하며, WALLET UPDATE는 flush 때 dirty checking으로 실행된다.
     *
     * @param memberId 잔액을 받을 회원 ID
     * @param amount   더할 금액(양수)
     * @param type     내역 유형({@code CANCEL_REFUND}, {@code REFUND}, {@code SALE_PAYOUT})
     * @param orderId  관련 주문 ID
     * @param now      변경 시각. 지갑의 UPDATED_AT과 내역의 CREATED_AT에 사용한다.
     * @throws BusinessException 증가 후 잔액이 저장 범위를 넘으면 {@code VALIDATION_ERROR}
     */
    @Override
    @Transactional
    public void credit(String memberId, long amount, BalanceTransactionType type, Long orderId, Instant now) {
        // 지갑 조회. 같은 트랜잭션에서 이미 조회한 지갑이면 영속성 컨텍스트의 같은 객체를 반환한다.
        Wallet wallet = getOrOpen(memberId, now);
        if (!AmountRules.fitsBalance(wallet.getBalance(), amount)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "반환·지급 후 잔액이 저장 가능한 범위를 초과합니다.");
        }
        // 잔액 증가와 내역 객체 생성 후 내역 INSERT. IDENTITY 전략이라 INSERT는 save 호출 시점에 실행된다.
        balanceTransactionRepository.save(wallet.apply(type, amount, orderId, null, now));
        logger.info("[잔액 증가] memberId={}, orderId={}, type={}, amount={}, balanceAfter={}",
                memberId, orderId, type, amount, wallet.getBalance());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceTransactionResponse> transactionsForOrder(String memberId, Long orderId) {
        return balanceTransactionRepository.findByMemberIdAndOrderIdOrderByTransactionIdAsc(memberId, orderId).stream()
                .map(BalanceTransactionResponse::of)
                .toList();
    }

    /**
     * 회원의 지갑을 조회하고, 없으면 잔액 0인 지갑 행을 만든다.
     *
     * 지갑 행은 회원 가입 때 만들지 않고, 충전·조회·지급에서 처음 필요할 때 만든다.
     * {@code orElseGet}을 쓰므로 지갑이 있으면 생성 람다는 실행되지 않고, 기존 행의 잔액과 UPDATED_AT도 바뀌지 않는다.
     *
     * @param memberId 회원 ID
     * @param now      새 지갑의 UPDATED_AT. 지갑을 새로 만들 때만 사용한다.
     * @return 기존 지갑 또는 새로 저장한 지갑
     */
    private Wallet getOrOpen(String memberId, Instant now) {
        return walletRepository.findById(memberId)
                .orElseGet(() -> walletRepository.save(Wallet.open(memberId, now)));
    }
}
