package com.chatservice.marketplace.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.chatservice.marketplace.support.IntegrationTestSupport;

/** F-003 테스트 잔액 충전 검증. */
class WalletChargeTest extends IntegrationTestSupport {

	@Autowired
	private IWalletService walletService;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private BalanceTransactionRepository transactionRepository;

	@Test
	void F003_AC02_잔액_0에서_10000원을_두_번_따로_충전하면_잔액_20000과_내역_2건() {
		String me = member("it_buyer", "구매자");

		ChargeResponse first = walletService.charge(me, new ChargeRequest(10_000L, "req-1"));
		ChargeResponse second = walletService.charge(me, new ChargeRequest(10_000L, "req-2"));

		assertThat(first.balance()).isEqualTo(10_000L);
		assertThat(second.balance()).isEqualTo(20_000L);
		assertThat(walletRepository.findById(me).orElseThrow().getBalance()).isEqualTo(20_000L);

		List<BalanceTransaction> txs = transactionRepository.findByMemberIdOrderByTransactionIdDesc(me);
		assertThat(txs).hasSize(2);
		assertThat(txs).allSatisfy(tx -> {
			assertThat(tx.getType()).isEqualTo(TransactionType.CHARGE);
			assertThat(tx.getAmount()).isEqualTo(10_000L);
			assertThat(tx.getOrderId()).isNull();
		});
		assertThat(txs).extracting(BalanceTransaction::getBalanceAfter).containsExactly(20_000L, 10_000L);
		assertThat(txs).extracting(BalanceTransaction::getRequestId).containsExactly("req-2", "req-1");
	}

	@Test
	void 충전_API는_201과_변동_후_잔액_내역을_돌려준다() throws Exception {
		String me = member("it_buyer", "구매자");

		mockMvc.perform(post("/api/wallet/charges").cookie(authCookie(me))
				.contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10000,\"requestId\":\"abc\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.balance").value(10000))
				.andExpect(jsonPath("$.transaction.transactionId").isNumber())
				.andExpect(jsonPath("$.transaction.type").value("CHARGE"))
				.andExpect(jsonPath("$.transaction.amount").value(10000))
				.andExpect(jsonPath("$.transaction.balanceAfter").value(10000))
				.andExpect(jsonPath("$.transaction.createdAt").exists());
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"amount\":0}", "{\"amount\":-1}", "{\"amount\":10.5}", "{}", "{\"amount\":\"abc\"}" })
	void 금액이_유효하지_않으면_400이고_잔액과_내역이_바뀌지_않는다(String body) throws Exception {
		String me = member("it_buyer", "구매자");

		mockMvc.perform(post("/api/wallet/charges").cookie(authCookie(me))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		assertThat(walletRepository.findById(me)).isEmpty();
		assertThat(transactionRepository.count()).isZero();
	}

	@Test
	void requestId_가_64자를_넘으면_400() throws Exception {
		String me = member("it_buyer", "구매자");
		mockMvc.perform(post("/api/wallet/charges").cookie(authCookie(me))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":100,\"requestId\":\"" + "x".repeat(65) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.requestId").exists());
	}

	@Test
	void 인증_없이_충전하면_401() throws Exception {
		mockMvc.perform(post("/api/wallet/charges")
				.contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
				.andExpect(status().isUnauthorized());
		assertThat(transactionRepository.count()).isZero();
	}
}
