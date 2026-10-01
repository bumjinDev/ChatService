package com.chatservice.marketplace.order;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.common.TimeRules;
import com.chatservice.marketplace.conversation.ConversationNotifier;
import com.chatservice.marketplace.conversation.IConversationService;
import com.chatservice.marketplace.offer.IOfferService;
import com.chatservice.marketplace.offer.PriceOffer;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductRepository;
import com.chatservice.marketplace.wallet.IWalletService;
import com.chatservice.marketplace.wallet.TransactionType;

/** 주문 생성과 잔액 결제(F-010). */
@Service
public class OrderService implements IOrderService {

	private static final Logger log = LoggerFactory.getLogger(OrderService.class);

	private final ProductRepository productRepository;
	private final PurchaseOrderRepository orderRepository;
	private final IOfferService offerService;
	private final IWalletService walletService;
	private final IConversationService conversationService;
	private final ConversationNotifier conversationNotifier;
	private final OrderDetailAssembler assembler;
	private final TimeRules timeRules;
	private final Clock clock;

	public OrderService(ProductRepository productRepository, PurchaseOrderRepository orderRepository,
			IOfferService offerService, IWalletService walletService, IConversationService conversationService,
			ConversationNotifier conversationNotifier, OrderDetailAssembler assembler, TimeRules timeRules,
			Clock clock) {
		this.productRepository = productRepository;
		this.orderRepository = orderRepository;
		this.offerService = offerService;
		this.walletService = walletService;
		this.conversationService = conversationService;
		this.conversationNotifier = conversationNotifier;
		this.assembler = assembler;
		this.timeRules = timeRules;
		this.clock = clock;
	}

	/**
	 * 처리 순서(설계 명세서 6.10절)
	 * 1. 상품이 없으면 404, 본인 상품이면 403 SELF_TRADE_NOT_ALLOWED, 판매 중이 아니면 409 PRODUCT_NOT_ON_SALE
	 * 2. 적용 가격: offerId 가 없으면 등록 가격(LISTED), 있으면 검증된 합의 가격(AGREED)
	 * 3. 잔액이 적용 가격보다 작으면 409 INSUFFICIENT_BALANCE
	 * 4. 주문 생성 → 잔액 차감과 PURCHASE 내역 → 상품 판매 종료 → 당사자 대화가 없으면 생성.
	 *    다섯 변경은 이 메서드의 트랜잭션 하나로 반영한다
	 * 5. 커밋 뒤 그 상품의 모든 대화에 CONVERSATION_STATE 이벤트를 보낸다
	 */
	@Override
	@Transactional
	public OrderDetailResponse placeOrder(String buyerId, OrderPlaceRequest request) {
		Product product = productRepository.findById(request.productId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		if (product.getSellerId().equals(buyerId)) {
			throw new BusinessException(ErrorCode.SELF_TRADE_NOT_ALLOWED);
		}
		if (!product.isOnSale()) {
			throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
		}

		long price = product.getPrice();
		PriceSource source = PriceSource.LISTED;
		Long offerId = null;
		if (request.offerId() != null) {
			PriceOffer offer = offerService.requireAgreedOffer(request.offerId(), product.getProductId(), buyerId);
			price = offer.getAmount();
			source = PriceSource.AGREED;
			offerId = offer.getOfferId();
		}

		if (walletService.balanceOf(buyerId) < price) {
			throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
		}

		Instant now = clock.instant();
		PurchaseOrder order = orderRepository.save(PurchaseOrder.confirm(product.getProductId(), buyerId,
				product.getSellerId(), price, source, offerId, request.recipientName(), request.shippingAddress(),
				now, timeRules.shipDeadlineAt(now), request.requestId()));
		walletService.debit(buyerId, TransactionType.PURCHASE, price, order.getOrderId(), null, now);
		product.markSold(now);
		conversationService.ensurePartyConversation(product.getProductId(), buyerId, product.getSellerId(), now);
		log.info("결제 성공 orderId={} memberId={} productId={} 상품 ON_SALE -> SOLD, 주문 -> {}/{} paidAmount={} source={}",
				order.getOrderId(), buyerId, product.getProductId(), order.getTradeStatus(),
				order.getShippingStatus(), price, source);

		conversationNotifier.notifyProductConversations(product.getProductId(), buyerId);
		return assembler.detail(order, buyerId);
	}
}
