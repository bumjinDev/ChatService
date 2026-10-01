package com.chatservice.marketplace.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.order.PriceSource;
import com.chatservice.marketplace.order.PurchaseOrder;
import com.chatservice.marketplace.order.PurchaseOrderRepository;
import com.chatservice.marketplace.product.Category;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductRepository;
import com.chatservice.marketplace.support.IntegrationTestSupport;

/** F-004 잔액·변동 내역 조회 검증. */
class WalletQueryTest extends IntegrationTestSupport {

	@Autowired
	private IWalletService walletService;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private BalanceTransactionRepository transactionRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private PurchaseOrderRepository orderRepository;

	/** 구매·반환 내역을 저장소로 직접 만든다. 결제와 취소 기능 자체는 F-010, F-012 테스트가 검증한다. */
	private Long orderFixture(String buyer, String seller, long amount) {
		Instant now = clock.instant();
		Product product = productRepository.saveAndFlush(
				Product.register(seller, "상품", "설명", Category.ETC, amount, now));
		PurchaseOrder order = orderRepository.saveAndFlush(PurchaseOrder.confirm(product.getProductId(), buyer,
				seller, amount, PriceSource.LISTED, null, "수령인", "주소", now, now.plus(Duration.ofDays(7)), null));
		return order.getOrderId();
	}

	@Test
	void F004_AC01_충전_구매_반환_뒤_본인이_조회하면_유형_금액_잔액_시각_주문이_맞다() {
		String me = member("it_buyer", "구매자");
		String seller = member("it_seller", "판매자");
		Long orderId = orderFixture(me, seller, 30_000L);

		Instant t1 = clock.instant();
		walletService.charge(me, new ChargeRequest(50_000L, null));
		clock.advance(Duration.ofMinutes(1));
		Instant t2 = clock.instant();
		inTransaction(() -> {
			Wallet wallet = walletRepository.findById(me).orElseThrow();
			long after = wallet.withdraw(30_000L, t2);
			transactionRepository.save(BalanceTransaction.record(me, TransactionType.PURCHASE, -30_000L, after,
					orderId, null, t2));
		});
		clock.advance(Duration.ofMinutes(1));
		Instant t3 = clock.instant();
		walletService.credit(me, TransactionType.CANCEL_REFUND, 30_000L, orderId, null, t3);

		assertThat(walletService.getMyWallet(me).balance()).isEqualTo(50_000L);
		List<TransactionResponse> txs = walletService.getMyTransactions(me);
		assertThat(txs).extracting(TransactionResponse::type)
				.containsExactly(TransactionType.CANCEL_REFUND, TransactionType.PURCHASE, TransactionType.CHARGE);
		assertThat(txs).extracting(TransactionResponse::amount).containsExactly(30_000L, -30_000L, 50_000L);
		assertThat(txs).extracting(TransactionResponse::balanceAfter).containsExactly(50_000L, 20_000L, 50_000L);
		assertThat(txs).extracting(TransactionResponse::createdAt).containsExactly(t3, t2, t1);
		assertThat(txs).extracting(TransactionResponse::orderId).containsExactly(orderId, orderId, null);
	}

	@Test
	void 지갑이_없는_회원이_조회하면_잔액_0인_지갑이_만들어진다() throws Exception {
		String me = member("it_buyer", "구매자");

		mockMvc.perform(get("/api/wallet").cookie(authCookie(me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.memberId").value(me))
				.andExpect(jsonPath("$.balance").value(0));
		mockMvc.perform(get("/api/wallet/transactions").cookie(authCookie(me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		assertThat(walletRepository.findById(me)).isPresent();
	}

	@Test
	void F004_AC02_다른_회원의_잔액은_조회할_수_없고_본인_잔액만_돌아온다() throws Exception {
		String me = member("it_buyer", "구매자");
		String other = member("it_other", "다른회원");
		walletService.charge(other, new ChargeRequest(70_000L, null));

		// 경로에 회원 ID 를 받지 않는다. 다른 회원 ID 를 쿼리로 보내도 무시되고 본인 잔액이 돌아온다.
		mockMvc.perform(get("/api/wallet").param("memberId", other).cookie(authCookie(me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.memberId").value(me))
				.andExpect(jsonPath("$.balance").value(0));
		mockMvc.perform(get("/api/wallet/transactions").param("memberId", other).cookie(authCookie(me)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/wallet/" + other).cookie(authCookie(me)))
				.andExpect(status().is4xxClientError());

		assertThat(walletRepository.findById(other).orElseThrow().getBalance()).isEqualTo(70_000L);
	}

	@Test
	void 인증_없이_조회하면_401() throws Exception {
		mockMvc.perform(get("/api/wallet/transactions")).andExpect(status().isUnauthorized());
	}
}
