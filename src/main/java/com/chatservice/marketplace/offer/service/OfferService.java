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
import com.chatservice.marketplace.offer.domain.AgreementStatus;
import com.chatservice.marketplace.offer.domain.OfferStatus;
import com.chatservice.marketplace.offer.domain.PriceOffer;
import com.chatservice.marketplace.offer.dto.OfferResponse;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.service.IProductService;
import com.chatservice.marketplace.reservation.dto.ReservationResponse;
import com.chatservice.marketplace.reservation.service.IReservationQueryService;
import com.chatservice.marketplace.reservation.service.IReservationService;

@Service
public class OfferService implements IOfferService {

    private static final Logger logger = LoggerFactory.getLogger(OfferService.class);

    private final PriceOfferRepository priceOfferRepository;
    private final IConversationService conversationService;
    private final IProductService productService;
    private final IReservationService reservationService;
    private final IReservationQueryService reservationQueryService;
    private final IAgreementQueryService agreementQueryService;
    private final RealtimePublisher realtimePublisher;
    private final TimeRules timeRules;

    public OfferService(PriceOfferRepository priceOfferRepository, IConversationService conversationService,
                        IProductService productService, IReservationService reservationService,
                        IReservationQueryService reservationQueryService, IAgreementQueryService agreementQueryService,
                        RealtimePublisher realtimePublisher, TimeRules timeRules) {
        this.priceOfferRepository = priceOfferRepository;
        this.conversationService = conversationService;
        this.productService = productService;
        this.reservationService = reservationService;
        this.reservationQueryService = reservationQueryService;
        this.agreementQueryService = agreementQueryService;
        this.realtimePublisher = realtimePublisher;
        this.timeRules = timeRules;
    }

    /*
     * 처리 순서(설계 6.8, 가격 제안 수량 설계안 5.1): 대화·참여자(404/403) → 구매 희망자인지(403 NOT_BUYER) → 판매 중인지(409) →
     * 금액 < 등록 가격(400) → 제안 수량 ≤ 구매 가능 수량(409 OFFER_QUANTITY_EXCEEDED) → 응답 대기 제안이 없는지(409) →
     * 수락된 제안의 합의가 예약 중·결제됨이 아닌지(409 OFFER_ALREADY_ACCEPTED) → 저장 → 판매자 세션에 OFFER 전달.
     * 합의가 만료되거나 해제된 수락 제안은 새 제안을 막지 않는다.
     * 수량 검사는 잠금 없이 조회한 구매 가능 수량으로 한다. 한 대화에 응답 대기 제안을 하나로 지키는 것도 순차 요청의 상태 검사다.
     * 동시 제안은 후속 과제다.
     */
    @Override
    @Transactional
    public OfferResponse propose(String memberId, Long conversationId, long amount, long quantity, String requestId) {
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
        // 구매 희망자 자신에게 걸린 예약은 빼지 않은 구매 가능 수량과 비교한다.
        if (quantity > reservationQueryService.availableQuantity(product, memberId)) {
            throw new BusinessException(ErrorCode.OFFER_QUANTITY_EXCEEDED);
        }
        if (priceOfferRepository.existsByConversationIdAndStatus(conversationId, OfferStatus.PENDING)) {
            throw new BusinessException(ErrorCode.OFFER_PENDING_EXISTS);
        }
        boolean agreementInEffect = priceOfferRepository.findByConversationIdAndStatus(conversationId, OfferStatus.ACCEPTED)
                .stream()
                .map(agreementQueryService::statusOf)
                .anyMatch(status -> status == AgreementStatus.RESERVED || status == AgreementStatus.ORDERED);
        if (agreementInEffect) {
            throw new BusinessException(ErrorCode.OFFER_ALREADY_ACCEPTED);
        }
        PriceOffer offer = priceOfferRepository.save(PriceOffer.propose(conversationId, product.getProductId(),
                memberId, conversation.getSellerId(), amount, quantity, requestId, timeRules.now()));
        logger.info("[가격 제안] offerId={}, conversationId={}, memberId={}, quantity={}, status={}",
                offer.getOfferId(), conversationId, memberId, quantity, offer.getStatus());
        publish(offer, memberId);
        return OfferResponse.of(offer);
    }

    /*
     * 처리 순서(설계 6.9, 가격 제안 수량 설계안 5.3): 제안(404) → 판매자인지(403) → 판매 중인지(409, 제안 상태는 그대로) →
     * 응답 대기인지(409) → 응답 기록 → [수락이면 같은 트랜잭션에서 제안 수량만큼 예약 걸기] → 구매 희망자 세션에 OFFER 전달.
     * 수락하면 합의가 성립하고 제안 수량만큼 예약이 걸린다. 공개 등록 가격과 재고는 바꾸지 않는다.
     * 예약 걸기가 409 로 거절되면 트랜잭션이 롤백되어 수락도 반영되지 않는다.
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
        ReservationResponse reservation = null;
        if (accept) {
            // 수락과 예약을 한 트랜잭션에서 처리한다. 예약 걸기가 409 로 거절되면 이 수락도 롤백된다.
            reservation = reservationService.reserve(memberId, offer.getConversationId(), offer.getQuantity(),
                    offer.getOfferId());
        }
        publish(offer, memberId);
        return OfferResponse.of(offer, reservation);
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
                OfferEvent.of(offer.getOfferId(), offer.getConversationId(), offer.getAmount(), offer.getQuantity(),
                        offer.getStatus().name(), offer.getCreatedAt(), offer.getRespondedAt()),
                actorId);
    }
}
