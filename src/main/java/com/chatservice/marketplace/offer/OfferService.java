package com.chatservice.marketplace.offer;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.common.MemberRole;
import com.chatservice.marketplace.conversation.Conversation;
import com.chatservice.marketplace.conversation.ConversationAccess;
import com.chatservice.marketplace.conversation.realtime.RealtimeEvents.OfferEvent;
import com.chatservice.marketplace.conversation.realtime.RealtimePublisher;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductRepository;

@Service
public class OfferService implements IOfferService {

	private static final Logger log = LoggerFactory.getLogger(OfferService.class);

	private final PriceOfferRepository offerRepository;
	private final ConversationAccess conversationAccess;
	private final ProductRepository productRepository;
	private final RealtimePublisher realtimePublisher;
	private final Clock clock;

	public OfferService(PriceOfferRepository offerRepository, ConversationAccess conversationAccess,
			ProductRepository productRepository, RealtimePublisher realtimePublisher, Clock clock) {
		this.offerRepository = offerRepository;
		this.conversationAccess = conversationAccess;
		this.productRepository = productRepository;
		this.realtimePublisher = realtimePublisher;
		this.clock = clock;
	}

	/**
	 * 처리 순서(설계 명세서 6.8절)
	 * 1. 대화가 없으면 404, 참여자가 아니면 403 NOT_CONVERSATION_MEMBER, 판매자이면 403 NOT_BUYER
	 * 2. 상품이 판매 중이 아니면 409 PRODUCT_NOT_ON_SALE
	 * 3. 금액이 등록 가격 이상이면 400 VALIDATION_ERROR
	 * 4. 응답 대기 제안이 있으면 409 OFFER_PENDING_EXISTS, 수락된 제안이 있으면 409 OFFER_ALREADY_ACCEPTED
	 * 5. PENDING 제안을 만들고 커밋 뒤 판매자 세션에 OFFER 이벤트를 보낸다
	 */
	@Override
	@Transactional
	public OfferResponse propose(String memberId, Long conversationId, OfferProposeRequest request) {
		Conversation conversation = conversationAccess.requireParticipant(memberId, conversationId);
		if (conversation.roleOf(memberId) != MemberRole.BUYER) {
			throw new BusinessException(ErrorCode.NOT_BUYER);
		}
		Product product = productRepository.findById(conversation.getProductId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		if (!product.isOnSale()) {
			throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
		}
		if (request.amount() >= product.getPrice()) {
			throw BusinessException.invalidField("amount", "제안 금액은 등록 가격보다 작아야 합니다.");
		}
		if (offerRepository.existsByConversationIdAndStatus(conversationId, OfferStatus.PENDING)) {
			throw new BusinessException(ErrorCode.OFFER_PENDING_EXISTS);
		}
		if (offerRepository.existsByConversationIdAndStatus(conversationId, OfferStatus.ACCEPTED)) {
			throw new BusinessException(ErrorCode.OFFER_ALREADY_ACCEPTED);
		}
		PriceOffer offer = offerRepository.save(PriceOffer.propose(conversationId, product.getProductId(), memberId,
				conversation.getSellerId(), request.amount(), request.requestId(), clock.instant()));
		log.info("가격 제안 offerId={} conversationId={} memberId={} status={}", offer.getOfferId(), conversationId,
				memberId, offer.getStatus());
		realtimePublisher.publish(conversationId, toEvent(offer), memberId);
		return OfferResponse.of(offer);
	}

	/**
	 * 처리 순서(설계 명세서 6.9절)
	 * 1. 제안이 없으면 404, 요청한 회원이 판매자가 아니면 403 NOT_SELLER
	 * 2. 상품이 판매 중이 아니면 409 PRODUCT_NOT_ON_SALE. 제안 상태는 바꾸지 않는다
	 * 3. 제안이 PENDING 이 아니면 409 OFFER_ALREADY_RESPONDED
	 * 4. 상태와 응답 시각을 기록하고 커밋 뒤 구매 희망자 세션에 OFFER 이벤트를 보낸다
	 * 수락해도 상품을 예약하거나 등록 가격을 바꾸지 않는다.
	 */
	@Override
	@Transactional
	public OfferResponse respond(String memberId, Long offerId, boolean accept) {
		PriceOffer offer = offerRepository.findById(offerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.OFFER_NOT_FOUND));
		if (!offer.getSellerId().equals(memberId)) {
			throw new BusinessException(ErrorCode.NOT_SELLER);
		}
		Product product = productRepository.findById(offer.getProductId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		if (!product.isOnSale()) {
			throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
		}
		if (offer.getStatus() != OfferStatus.PENDING) {
			throw new BusinessException(ErrorCode.OFFER_ALREADY_RESPONDED);
		}
		OfferStatus before = offer.getStatus();
		offer.respond(accept, clock.instant());
		log.info("가격 제안 응답 offerId={} conversationId={} memberId={} {} -> {}", offerId,
				offer.getConversationId(), memberId, before, offer.getStatus());
		realtimePublisher.publish(offer.getConversationId(), toEvent(offer), memberId);
		return OfferResponse.of(offer);
	}

	static OfferEvent toEvent(PriceOffer offer) {
		return new OfferEvent(offer.getOfferId(), offer.getConversationId(), offer.getAmount(), offer.getStatus(),
				offer.getCreatedAt(), offer.getRespondedAt());
	}
}
