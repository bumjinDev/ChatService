package com.chatservice.marketplace.wallet;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 잔액(WALLET)과 변동 내역(BALANCE_TRANSACTION)을 함께 바꾸는 곳이다.
 * 잔액 증감과 내역 기록은 항상 같은 트랜잭션 안에서 일어난다(BR-003).
 */
@Service
public class WalletService implements IWalletService {

	private static final Logger log = LoggerFactory.getLogger(WalletService.class);

	private final WalletRepository walletRepository;
	private final BalanceTransactionRepository transactionRepository;
	private final Clock clock;

	public WalletService(WalletRepository walletRepository, BalanceTransactionRepository transactionRepository,
			Clock clock) {
		this.walletRepository = walletRepository;
		this.transactionRepository = transactionRepository;
		this.clock = clock;
	}

	@Override
	@Transactional
	public ChargeResponse charge(String memberId, ChargeRequest request) {
		BalanceTransaction tx = credit(memberId, TransactionType.CHARGE, request.amount(), null, request.requestId(),
				clock.instant());
		return new ChargeResponse(tx.getBalanceAfter(), TransactionResponse.of(tx));
	}

	@Override
	@Transactional
	public WalletResponse getMyWallet(String memberId) {
		Wallet wallet = getOrOpen(memberId, clock.instant());
		return new WalletResponse(wallet.getMemberId(), wallet.getBalance());
	}

	@Override
	@Transactional
	public List<TransactionResponse> getMyTransactions(String memberId) {
		getOrOpen(memberId, clock.instant());
		return transactionRepository.findByMemberIdOrderByTransactionIdDesc(memberId).stream()
				.map(TransactionResponse::of)
				.toList();
	}

	@Override
	@Transactional
	public BalanceTransaction credit(String memberId, TransactionType type, long amount, Long orderId,
			String requestId, Instant now) {
		Wallet wallet = getOrOpen(memberId, now);
		long balanceAfter = wallet.deposit(amount, now);
		BalanceTransaction tx = transactionRepository.save(
				BalanceTransaction.record(memberId, type, amount, balanceAfter, orderId, requestId, now));
		log.info("잔액 증가 memberId={} type={} amount={} balanceAfter={} orderId={}", memberId, type, amount,
				balanceAfter, orderId);
		return tx;
	}

	/** 지갑을 조회하고, 없으면 잔액 0 인 지갑을 만든다. */
	Wallet getOrOpen(String memberId, Instant now) {
		return walletRepository.findById(memberId)
				.orElseGet(() -> walletRepository.save(Wallet.open(memberId, now)));
	}
}
