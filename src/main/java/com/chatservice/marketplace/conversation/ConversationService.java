package com.chatservice.marketplace.conversation;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductRepository;

@Service
public class ConversationService implements IConversationService {

	private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

	private final ConversationRepository conversationRepository;
	private final ProductRepository productRepository;
	private final ConversationAssembler assembler;
	private final Clock clock;

	public ConversationService(ConversationRepository conversationRepository, ProductRepository productRepository,
			ConversationAssembler assembler, Clock clock) {
		this.conversationRepository = conversationRepository;
		this.productRepository = productRepository;
		this.assembler = assembler;
		this.clock = clock;
	}

	/**
	 * 처리 순서(설계 명세서 6.5절)
	 * 1. 상품이 없으면 404
	 * 2. 본인 상품이면 403 SELF_TRADE_NOT_ALLOWED
	 * 3. 같은 상품·구매 희망자의 대화가 있으면 판매 상태와 관계없이 그 대화를 돌려준다
	 * 4. 없을 때 상품이 판매 중이 아니면 409 PRODUCT_NOT_ON_SALE
	 * 5. 새 대화를 만든다
	 */
	@Override
	@Transactional
	public ConversationStartResult start(String memberId, Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		if (product.getSellerId().equals(memberId)) {
			throw new BusinessException(ErrorCode.SELF_TRADE_NOT_ALLOWED);
		}
		Optional<Conversation> existing = conversationRepository
				.findFirstByProductIdAndBuyerIdOrderByConversationIdAsc(productId, memberId);
		if (existing.isPresent()) {
			return new ConversationStartResult(false, assembler.detail(existing.get(), memberId));
		}
		if (!product.isOnSale()) {
			throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
		}
		Conversation conversation = conversationRepository.save(
				Conversation.open(productId, memberId, product.getSellerId(), clock.instant(), false));
		log.info("대화 생성 conversationId={} productId={} memberId={}", conversation.getConversationId(), productId,
				memberId);
		return new ConversationStartResult(true, assembler.detail(conversation, memberId));
	}
}
