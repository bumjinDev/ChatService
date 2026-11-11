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
import com.chatservice.marketplace.testsupport.TradeTestLog;

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

    /**
     * 판매자가 가격 제안을 수락하거나 거절한다.
     *
     * 가격 제안·판매자·상품 검사를 모두 마친 뒤 가격 제안의 응답 상태와 응답 시각을 바꾼다.
     * 수락이면 같은 트랜잭션에서 가격 제안을 한 구매 희망자에게 제안 수량만큼 예약을 건다. 예약이 거절되면 수락도 반영되지 않는다.
     * 등록 가격과 상품의 남은 수량은 바꾸지 않는다. 가격 제안 이벤트는 커밋 후 전송된다.
     *
     * @param memberId 응답을 요청한 회원 ID(인증 정보에서 얻은 값)
     * @param offerId  응답할 가격 제안 ID
     * @param accept   수락이면 {@code true}, 거절이면 {@code false}
     * @return 응답한 가격 제안. 수락이면 함께 건 예약을 담는다
     * @throws BusinessException 다음 경우에 던지며, 이때 데이터는 변경되지 않는다.
     *           - {@code OFFER_NOT_FOUND}: 가격 제안이 없다
     *           - {@code NOT_SELLER}: 요청한 회원이 가격 제안을 받은 판매자가 아니다
     *           - {@code PRODUCT_NOT_FOUND}: 상품이 없다
     *           - {@code PRODUCT_NOT_ON_SALE}: 판매 종료된 상품이다
     *           - {@code OFFER_ALREADY_RESPONDED}: 이미 수락했거나 거절한 가격 제안이다
     *           - {@code CONVERSATION_NOT_FOUND}, {@code NOT_CONVERSATION_MEMBER}: 수락하려는데 가격 제안의 대화가 없거나 요청한 회원이 그 대화의 참여자가 아니다
     *           - {@code RESERVATION_ALREADY_ACTIVE}: 수락하려는데 같은 구매 희망자에게 만료 전인 예약이 이미 있다
     *           - {@code RESERVATION_QUANTITY_EXCEEDED}: 수락하려는데 제안 수량이 더 걸 수 있는 예약 수량보다 많다
     */
    @Override
    @Transactional
    public OfferResponse respond(String memberId, Long offerId, boolean accept) {
        if (accept) {
            TradeTestLog.registerTransactionEnd(logger, "가격 제안 수락", "OfferService.java", "respond",
                    "제안ID", offerId);
        }
        try {
            // 가격 제안 조회(테이블 "PRICE_OFFER"). 없으면 OFFER_NOT_FOUND
            PriceOffer offer = priceOfferRepository.findById(offerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.OFFER_NOT_FOUND));
            if (accept) {
                TradeTestLog.event(logger, "가격 제안 수락", "OfferService.java", "respond", "수락시작",
                        "상품ID", offer.getProductId(), "제안ID", offer.getOfferId(), "대화ID", offer.getConversationId(),
                        "판매자ID", offer.getSellerId(), "구매자ID", offer.getBuyerId(),
                        "요청수량", offer.getQuantity(), "제안상태", offer.getStatus());
            }
            // 가격 제안을 받은 판매자 본인만 응답할 수 있다
            if (!offer.getSellerId().equals(memberId)) {
                throw new BusinessException(ErrorCode.NOT_SELLER);
            }
            // 상품 조회(테이블 "PRODUCT"). 없으면 PRODUCT_NOT_FOUND
            Product product = productService.getProduct(offer.getProductId());
            // 판매 종료 상품의 가격 제안에는 응답할 수 없다
            if (!product.isOnSale()) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
            }
            // 이미 수락했거나 거절한 가격 제안에는 다시 응답할 수 없다
            if (!offer.isPending()) {
                throw new BusinessException(ErrorCode.OFFER_ALREADY_RESPONDED);
            }

            /* 응답이 안되는 기준을 전부 통과 후에 실제로 가격 제안 상태를 바꾸는 위치 */

            // 로깅용 - 바꾸기 전 가격 제안 상태
            OfferStatus before = offer.getStatus();
            // 가격 제안 상태 변경(테이블 "PRICE_OFFER") : 수락이면 ACCEPTED, 거절이면 REJECTED 로 바꾸고 응답 시각을 기록한다. 엔티티 필드만 바꾸며 UPDATE 는 flush 때 실행된다
            offer.respond(accept, timeRules.now());

            logger.info("[가격 제안 응답] offerId={}, memberId={}, {} -> {}", offerId, memberId, before, offer.getStatus());

            // 응답에 담을 예약 내역. 수락일 때만 채우고 거절이면 null 이다
            ReservationResponse reservation = null;

            /* 수락이면 같은 트랜잭션에서 예약 내역(수량 점유)까지 생성 */
            if (accept) {
                // 예약 내역 데이터 생성(테이블 "RESERVATION") : 가격 제안을 한 구매 희망자에게 제안 수량만큼 예약을 건다. 예약이 거절되면 이 수락도 롤백된다
                /* memberID : 컨트롤러에서부터 받은 이 제안을 수락하는 당사자인 판매자 ID
                *  Price_offer 테이블 Entity 속성 값 3 개
                *   - conversationId : 이 가격 제안이 등록된 대화 ID(컬럼 CONVERSATION_ID). 예약 생성에서 이 대화를 조회해서 상품과, 예약을 받을 구매 희망자를 알아낸다
                *   - quantity : 구매 희망자가 제안한 수량(컬럼 QUANTITY). 예약 생성에서 더 걸 수 있는 예약 수량과 비교하고, 통과하면 이 수량만큼 예약한다
                *   - offerId : 지금 수락하는 가격 제안 ID(컬럼 OFFER_ID). 예약 내역에 이 예약을 만든 가격 제안으로 저장한다
                * */
                reservation = reservationService.reserve(memberId,
                        offer.getConversationId(), offer.getQuantity(), offer.getOfferId());
            }

            // 대화 상대(구매 희망자)의 세션에 보낼 가격 제안 이벤트를 등록한다. 실제 전송은 커밋 후에 한다
            publish(offer, memberId);
            // 메서드가 반환되면 트랜잭션이 커밋된다
            if (accept) {
                TradeTestLog.event(logger, "가격 제안 수락", "OfferService.java", "respond", "커밋전",
                        "제안ID", offer.getOfferId(), "제안상태", offer.getStatus(),
                        "예약ID", reservation.reservationId(), "예약수량", reservation.quantity());
            }
            return OfferResponse.of(offer, reservation);
        } catch (RuntimeException e) {
            TradeTestLog.recordFailure(e);   // 업무 오류이면 오류 코드, 그 밖의 예외이면 예외 이름
            throw e;                         // 같은 예외를 그대로 다시 던짐
        }
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
        // 가격 제안의 대화에 OFFER 이벤트를 보낸다. 요청한 회원(actorId)의 세션에는 보내지 않는다. 실제 전송은 커밋 후에 한다
        realtimePublisher.publish(offer.getConversationId(),
                OfferEvent.of(offer.getOfferId(), offer.getConversationId(), offer.getAmount(), offer.getQuantity(),
                        offer.getStatus().name(), offer.getCreatedAt(), offer.getRespondedAt()),
                actorId);
    }
}
