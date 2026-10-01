package com.chatservice.marketplace.conversation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.common.MemberDirectory;
import com.chatservice.marketplace.conversation.ConversationSummaryResponse.ProductSummary;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductRepository;

@Service
public class ConversationService implements IConversationService {

	private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

	private final ConversationRepository conversationRepository;
	private final ProductRepository productRepository;
	private final ConversationAssembler assembler;
	private final ConversationAccess conversationAccess;
	private final ConversationStateEvaluator stateEvaluator;
	private final ChatMessageRepository messageRepository;
	private final MemberDirectory memberDirectory;
	private final Clock clock;

	public ConversationService(ConversationRepository conversationRepository, ProductRepository productRepository,
			ConversationAssembler assembler, ConversationAccess conversationAccess,
			ConversationStateEvaluator stateEvaluator, ChatMessageRepository messageRepository,
			MemberDirectory memberDirectory, Clock clock) {
		this.conversationRepository = conversationRepository;
		this.productRepository = productRepository;
		this.assembler = assembler;
		this.conversationAccess = conversationAccess;
		this.stateEvaluator = stateEvaluator;
		this.messageRepository = messageRepository;
		this.memberDirectory = memberDirectory;
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

	@Override
	@Transactional(readOnly = true)
	public List<ConversationSummaryResponse> listMine(String memberId) {
		List<Conversation> conversations = conversationRepository.findAllOfMember(memberId);
		Map<String, String> nicknames = memberDirectory
				.nicknames(conversations.stream().map(c -> c.counterpartOf(memberId)).toList());
		List<ConversationSummaryResponse> result = new ArrayList<>();
		for (Conversation conversation : conversations) {
			ConversationState state = stateEvaluator.evaluate(conversation);
			Product product = state.product();
			result.add(new ConversationSummaryResponse(
					conversation.getConversationId(),
					new ProductSummary(product.getProductId(), product.getName(), product.getPrice(),
							product.getStatus()),
					conversation.roleOf(memberId),
					nicknames.get(conversation.counterpartOf(memberId)),
					state.writable(),
					conversation.getCreatedAt()));
		}
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	public ConversationDetailResponse getDetail(String memberId, Long conversationId) {
		Conversation conversation = conversationAccess.requireParticipant(memberId, conversationId);
		return assembler.detail(conversation, memberId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<MessageResponse> getMessages(String memberId, Long conversationId, Long afterId) {
		conversationAccess.requireParticipant(memberId, conversationId);
		List<ChatMessage> messages = (afterId == null)
				? messageRepository.findByConversationIdOrderByMessageIdAsc(conversationId)
				: messageRepository.findByConversationIdAndMessageIdGreaterThanOrderByMessageIdAsc(conversationId,
						afterId);
		Map<String, String> nicknames = memberDirectory
				.nicknames(messages.stream().map(ChatMessage::getSenderId).toList());
		return messages.stream().map(m -> MessageResponse.of(m, nicknames.get(m.getSenderId()))).toList();
	}

	@Override
	@Transactional
	public Conversation ensurePartyConversation(Long productId, String buyerId, String sellerId,
			Instant now) {
		return conversationRepository.findFirstByProductIdAndBuyerIdOrderByConversationIdAsc(productId, buyerId)
				.orElseGet(() -> {
					Conversation created = conversationRepository
							.save(Conversation.open(productId, buyerId, sellerId, now, true));
					log.info("결제에 따른 당사자 대화 생성 conversationId={} productId={} memberId={}",
							created.getConversationId(), productId, buyerId);
					return created;
				});
	}
}
