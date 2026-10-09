package com.chatservice.marketplace.offer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.error.BusinessException;
import com.chatservice.marketplace.common.error.ErrorCode;
import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.conversation.domain.Conversation;
import com.chatservice.marketplace.conversation.realtime.RealtimePublisher;
import com.chatservice.marketplace.conversation.realtime.event.OfferEvent;
import com.chatservice.marketplace.conversation.service.IConversationService;
import com.chatservice.marketplace.offer.PriceOfferRepository;
import com.chatservice.marketplace.offer.domain.OfferStatus;
import com.chatservice.marketplace.offer.domain.PriceOffer;
import com.chatservice.marketplace.offer.dto.OfferResponse;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.service.IProductService;

@Service
public class OfferService implements IOfferService {

    private static final Logger logger = LoggerFactory.getLogger(OfferService.class);

    private final PriceOfferRepository priceOfferRepository;
    private final IConversationService conversationService;
    private final IProductService productService;
    private final RealtimePublisher realtimePublisher;
    private final TimeRules timeRules;

    public OfferService(PriceOfferRepository priceOfferRepository, IConversationService conversationService,
                        IProductService productService, RealtimePublisher realtimePublisher, TimeRules timeRules) {
        this.priceOfferRepository = priceOfferRepository;
        this.conversationService = conversationService;
        this.productService = productService;
        this.realtimePublisher = realtimePublisher;
        this.timeRules = timeRules;
    }

    /*
     * 처리 순서(설계 6.8): 대화·참여자(404/403) → 구매 희망자인지(403 NOT_BUYER) → 판매 중인지(409) →
     * 금액 < 등록 가격(400) → 응답 대기·수락 제안이 없는지(409) → 저장 → 판매자 세션에 OFFER 전달.
     * 한 대화에 응답 대기 제안을 하나로 지키는 것은 순차 요청의 상태 검사다. 동시 제안은 후속 과제다.
     */
    @Override
    @Transactional
    public OfferResponse propose(String memberId, Long conversationId, long amount, String requestId) {
        Conversation conversation = conversationService.getForMember(memberId, conversationId);
        if (!conversation.isBuyer(memberId)) {
            throw new BusinessException(ErrorCode.NOT_BUYER);
        }
        Product product = productService.getProduct(conversation.getProductId());
        if (!product.isOnSale()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        if (amount >= product.getPrice()) {
            throw BusinessException.invalidField("amount", "제안 금액은 개당 등록 가격보다 작아야 합니다.");
        }
        if (priceOfferRepository.existsByConversationIdAndStatus(conversationId, OfferStatus.PENDING)) {
            throw new BusinessException(ErrorCode.OFFER_PENDING_EXISTS);
        }
        if (priceOfferRepository.existsByConversationIdAndStatus(conversationId, OfferStatus.ACCEPTED)) {
            throw new BusinessException(ErrorCode.OFFER_ALREADY_ACCEPTED);
        }
        PriceOffer offer = priceOfferRepository.save(PriceOffer.propose(conversationId, product.getProductId(),
                memberId, conversation.getSellerId(), amount, requestId, timeRules.now()));
        logger.info("[가격 제안] offerId={}, conversationId={}, memberId={}, status={}",
                offer.getOfferId(), conversationId, memberId, offer.getStatus());
        publish(offer, memberId);
        return OfferResponse.of(offer);
    }

    /*
     * 처리 순서(설계 6.9): 제안(404) → 판매자인지(403) → 판매 중인지(409, 제안 상태는 그대로) →
     * 응답 대기인지(409) → 응답 기록 → 구매 희망자 세션에 OFFER 전달.
     * 수락해도 재고를 예약하지 않고 공개 등록 가격을 바꾸지 않는다.
     */
    @Override
    @Transactional
    public OfferResponse respond(String memberId, Long offerId, boolean accept) {
        PriceOffer offer = priceOfferRepository.findById(offerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OFFER_NOT_FOUND));
        if (!offer.getSellerId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOT_SELLER);
        }
        Product product = productService.getProduct(offer.getProductId());
        if (!product.isOnSale()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        if (!offer.isPending()) {
            throw new BusinessException(ErrorCode.OFFER_ALREADY_RESPONDED);
        }
        OfferStatus before = offer.getStatus();
        offer.respond(accept, timeRules.now());
        logger.info("[가격 제안 응답] offerId={}, memberId={}, {} -> {}", offerId, memberId, before, offer.getStatus());
        publish(offer, memberId);
        return OfferResponse.of(offer);
    }

    @Override
    @Transactional(readOnly = true)
    public PriceOffer acceptedOfferFor(Long offerId, Long productId, String buyerId) {
        /* 가격 제안 테이블(테이블 - PRICE_OFFER) 에서 제품 아이디와 구매자 ID 그리고 그 제안에 대한 수락 여부(PENDING : 수락 혹은 거부 대기중, ACCEPTED : 수락, REJECTED : 거부)를 확인해서 수락한 데이터만 가져오기 */
        return priceOfferRepository.findById(offerId)                                // PRICE_OFFER 테이블에서 ID 값으로 가져오기
                .filter(PriceOffer::isAccepted)                                      // 수락한 제안만 필터링해서 가져오기
                .filter(offer -> offer.getProductId().equals(productId))    // 판매자 ID 으로 필터링 해서 가져오기
                .filter(offer -> offer.getBuyerId().equals(buyerId))        // 구매자 ID 으로 필터링 해서 가져오기
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OFFER_SELECTION));
    }

    private void publish(PriceOffer offer, String actorId) {
        realtimePublisher.publish(offer.getConversationId(),
                OfferEvent.of(offer.getOfferId(), offer.getConversationId(), offer.getAmount(), offer.getStatus().name(),
                        offer.getCreatedAt(), offer.getRespondedAt()),
                actorId);
    }
}
