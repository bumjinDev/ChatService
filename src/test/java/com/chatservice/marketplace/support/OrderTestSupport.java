package com.chatservice.marketplace.support;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.offer.IOfferService;
import com.chatservice.marketplace.offer.OfferProposeRequest;
import com.chatservice.marketplace.offer.OfferResponse;
import com.chatservice.marketplace.conversation.Conversation;
import com.chatservice.marketplace.order.IOrderService;
import com.chatservice.marketplace.order.OrderDetailResponse;
import com.chatservice.marketplace.order.OrderPlaceRequest;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.wallet.BalanceTransaction;
import com.chatservice.marketplace.wallet.BalanceTransactionRepository;
import com.chatservice.marketplace.wallet.ChargeRequest;
import com.chatservice.marketplace.wallet.IWalletService;
import com.chatservice.marketplace.wallet.TransactionType;

/** 주문 기능 테스트의 공통 준비 작업. 실제 서비스(충전, 제안, 결제)를 호출해 상태를 만든다. */
public abstract class OrderTestSupport extends IntegrationTestSupport {

	@Autowired
	protected IWalletService walletService;

	@Autowired
	protected IOfferService offerService;

	@Autowired
	protected IOrderService orderService;

	@Autowired
	protected BalanceTransactionRepository transactionRepository;

	protected void charge(String memberId, long amount) {
		walletService.charge(memberId, new ChargeRequest(amount, null));
	}

	protected OrderPlaceRequest listedRequest(Product product) {
		return new OrderPlaceRequest(product.getProductId(), "홍길동", "서울시 중구 세종대로 1", null, null);
	}

	/** 잔액을 충전하고 등록 가격으로 결제한다. */
	protected OrderDetailResponse purchase(String buyerId, Product product) {
		charge(buyerId, product.getPrice());
		return orderService.placeOrder(buyerId, listedRequest(product));
	}

	/** 대화를 만들고 제안한 뒤 판매자가 수락한 제안을 돌려준다. */
	protected OfferResponse acceptedOffer(Product product, String buyerId, long amount) {
		Conversation conv = conversation(product, buyerId);
		OfferResponse offer = offerService.propose(buyerId, conv.getConversationId(),
				new OfferProposeRequest(amount, null));
		return offerService.respond(product.getSellerId(), offer.offerId(), true);
	}

	protected long balance(String memberId) {
		return walletService.balanceOf(memberId);
	}

	protected List<BalanceTransaction> transactions(String memberId, TransactionType type) {
		return transactionRepository.findByMemberIdOrderByTransactionIdDesc(memberId).stream()
				.filter(tx -> tx.getType() == type)
				.toList();
	}

	protected ErrorCode errorOf(Runnable action) {
		try {
			action.run();
		} catch (BusinessException e) {
			return e.getErrorCode();
		}
		throw new AssertionError("BusinessException 이 발생해야 한다");
	}
}
