package com.chatservice.marketplace.reservation.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.chatservice.marketplace.common.PartyRole;
import com.chatservice.marketplace.common.error.BusinessException;
import com.chatservice.marketplace.common.error.ErrorCode;
import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.conversation.domain.Conversation;
import com.chatservice.marketplace.conversation.service.IConversationService;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.service.IProductService;
import com.chatservice.marketplace.reservation.ReservationRepository;
import com.chatservice.marketplace.reservation.domain.Reservation;
import com.chatservice.marketplace.reservation.domain.ReservationStatus;
import com.chatservice.marketplace.reservation.dto.ReservationResponse;
import com.chatservice.marketplace.testsupport.PausePoint;
import com.chatservice.marketplace.testsupport.TestPause;

/**
 * 예약 걸기(가격 제안 수락과 함께), 해제, 결제 종료, 취소 뒤 새 예약, 만료 자동 해제.
 *
 * 모든 검사는 잠금 없이 조회한 값으로 하고 변경은 dirty checking 의 UPDATE 로 반영한다. 두 예약 요청이 같은 남은 수량과
 * 예약 합계를 읽거나 예약과 결제가 같은 값을 읽는 경합의 보장은 후속 검증 대상이다.
 */
@Service
public class ReservationService implements IReservationService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final IReservationQueryService reservationQueryService;
    private final IConversationService conversationService;
    private final IProductService productService;
    private final TransactionTemplate transactionTemplate;
    private final TimeRules timeRules;
    private final Duration reservationDuration;
    private final TestPause testPause;

    public ReservationService(ReservationRepository reservationRepository,
                              IReservationQueryService reservationQueryService,
                              IConversationService conversationService, IProductService productService,
                              TransactionTemplate transactionTemplate, TimeRules timeRules,
                              @Value("${marketplace.reservation.duration}") Duration reservationDuration,
                              TestPause testPause) {
        this.reservationRepository = reservationRepository;
        this.reservationQueryService = reservationQueryService;
        this.conversationService = conversationService;
        this.productService = productService;
        this.transactionTemplate = transactionTemplate;
        this.timeRules = timeRules;
        this.reservationDuration = reservationDuration;
        this.testPause = testPause;
    }

    /**
     * 판매자가 수락한 가격 제안의 구매 희망자에게 제안 수량만큼 예약을 건다.
     *
     * 대화·판매자·상품·기존 예약·수량 검사를 모두 마친 뒤 예약을 저장한다. 예약은 상품의 남은 수량을 줄이지 않는다.
     * 가격 제안 수락과 같은 트랜잭션에서 실행되므로, 여기서 거절되면 수락도 반영되지 않는다. 대화 상태 이벤트는 커밋 후 전송된다.
     *
     * @param memberId       예약을 거는 회원 ID. 가격 제안을 수락한 판매자다
     * @param conversationId 가격 제안이 있는 대화 ID. 이 대화의 구매 희망자에게 예약을 건다
     * @param quantity       예약 수량. 가격 제안의 제안 수량이다
     * @param offerId        이 예약을 만드는 가격 제안 ID
     * @return 저장한 예약
     * @throws BusinessException 다음 경우에 던지며, 이때 데이터는 변경되지 않는다.
     *           - {@code CONVERSATION_NOT_FOUND}: 대화가 없다
     *           - {@code NOT_CONVERSATION_MEMBER}: 요청한 회원이 대화의 참여자가 아니다
     *           - {@code NOT_SELLER}: 요청한 회원이 대화의 판매자가 아니다
     *           - {@code PRODUCT_NOT_FOUND}: 상품이 없다
     *           - {@code PRODUCT_NOT_ON_SALE}: 판매 종료된 상품이다
     *           - {@code RESERVATION_ALREADY_ACTIVE}: 같은 구매 희망자에게 만료 전인 예약이 이미 있다
     *           - {@code RESERVATION_QUANTITY_EXCEEDED}: 예약 수량이 더 걸 수 있는 예약 수량보다 많다
     */
    @Override
    @Transactional
    public ReservationResponse reserve(String memberId, Long conversationId, long quantity, Long offerId) {
        // 예약 요청을 처리하기 시작하는 시각. 기존 예약의 만료 여부 확인, 예약 시각, 만료 시각 계산에 같은 값을 쓴다
        Instant now = timeRules.now();

        // 대화 조회. 없으면 CONVERSATION_NOT_FOUND, 요청한 회원이 참여자가 아니면 NOT_CONVERSATION_MEMBER
        Conversation conversation = conversationService.getForMember(memberId, conversationId);
        // 대화의 판매자만 예약을 걸 수 있다
        if (!conversation.getSellerId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOT_SELLER);
        }
        // 상품 조회. 없으면 PRODUCT_NOT_FOUND. - 솔직히 바로 직전인 OfferService.respond() 내  "Product product = productService.getProduct(offer.getProductId());" 여기서 확인 했는데 왜 또 하는 이유는 모르겠음.
        Product product = productService.getProduct(conversation.getProductId());
        // 판매 종료 상품에는 예약을 걸 수 없다
        if (!product.isOnSale()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        // 판매자가 지금 수락하는 가격 제안의 구매 희망자에게, 같은 상품의 예약이 이미 걸려 있는지 확인한다
        // 걸려 있으면 true 이고, 한 구매 희망자에게 예약이 두 개 걸리지 않도록 아래에서 새 예약을 거절한다
        boolean activeExists = reservationRepository
                // 예약 테이블(테이블 "RESERVATION")에서 상품 ID 가 이 상품이고, 구매자 ID 가 가격 제안을 한 구매 희망자이고, 상태가 ACTIVE(예약 중)인 예약을 가져온다
                .findByProductIdAndBuyerIdAndStatus(product.getProductId(), conversation.getBuyerId(), ReservationStatus.ACTIVE)
                // 가져온 예약 가운데 만료 시각이 아직 지나지 않은 예약이 한 건이라도 있으면 true
                // 만료 시각이 지나도 상태는 바로 바뀌지 않고, 30초마다 실행되는 자동 해제가 EXPIRED 로 바꿀 때까지 ACTIVE 로 남는다
                // 그래서 상태가 ACTIVE 여도 만료 시각이 지난 예약은 이미 걸린 예약으로 보지 않는다
                .stream().anyMatch(existing -> existing.isActiveAt(now));
        // 같은 구매 희망자에게 두 번째 예약은 걸 수 없다
        if (activeExists) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_ACTIVE);
        }
        // 더 걸 수 있는 예약 수량 = 상품의 남은 수량 − 이 상품에서 만료 전인 예약 수량 전부. 잠금 없이 조회한 값으로 계산한다
        // 상품 조회·결제와 같이 쓰는 계산이라 회원 ID 를 받지만, 판매자 본인에게 걸린 예약은 없으므로 판매자 ID 를 넘기면 만료 전인 예약을 누구 것이든 모두 남은 수량에서 뺀다
        long limit = reservationQueryService.availableQuantity(product, memberId);
        // 더 걸 수 있는 예약 수량을 넘는 예약은 거절한다. 예외로 트랜잭션이 롤백되어 가격 제안의 수락도 반영되지 않는다
        if (quantity > limit) {
            throw new BusinessException(ErrorCode.RESERVATION_QUANTITY_EXCEEDED);
        }
        // 겹침 재현 테스트의 중지 위치. 같은 구매자의 예약과 수량 상한 검사를 통과한 직후, 예약을 저장하기 전이다. 테스트 설정이 꺼져 있거나 테스트 헤더가 없는 요청은 그대로 진행한다.
        testPause.reach(PausePoint.RESERVATION_CHECKED);

        /* 예약이 안되는 기준을 전부 통과 후에 실제로 예약을 생성하는 위치 */

        // 예약 내역 데이터 생성(테이블 "RESERVATION") : 예약 생성(예약 중). 만료 시각은 예약 시각에 예약 기간을 더한 값이다. IDENTITY 채번이라 저장 시점에 INSERT 되어 예약 ID 가 정해진다
        Reservation reservation = reservationRepository.save(Reservation.reserve(product.getProductId(), conversationId,
                conversation.getBuyerId(), memberId, offerId, quantity, now, now.plus(reservationDuration)));

        logger.info("[예약 걸기] reservationId={}, conversationId={}, productId={}, memberId={}, offerId={}, quantity={}, expiresAt={}",
                reservation.getReservationId(), conversationId, product.getProductId(), memberId, offerId, quantity,
                reservation.getExpiresAt());

        // 상품의 모든 대화에 보낼 상태 이벤트를 등록한다. 다른 대화의 구매 가능 수량이 바뀌기 때문이다. 실제 전송은 커밋 후에 한다
        conversationService.publishStateToProductConversations(product);
        // 메서드가 반환되면 가격 제안 수락 처리로 돌아가고, 수락 처리가 끝날 때 트랜잭션이 커밋된다
        return ReservationResponse.of(reservation);
    }

    /*
     * 처리 순서(설계안 5.3): 예약(404) → 판매자 또는 예약된 구매 희망자인지(403 NOT_CONVERSATION_MEMBER) →
     * 활성인지(409 RESERVATION_NOT_ACTIVE) → 해제 기록 → 상품의 모든 대화에 CONVERSATION_STATE(커밋 후).
     */
    @Override
    @Transactional
    public ReservationResponse release(String memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.isParty(memberId)) {
            throw new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER);
        }
        if (!reservation.isActive()) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_ACTIVE);
        }
        PartyRole by = reservation.isSeller(memberId) ? PartyRole.SELLER : PartyRole.BUYER;
        reservation.release(by, timeRules.now());
        logger.info("[예약 해제] reservationId={}, memberId={}, releasedBy={}", reservationId, memberId, by);
        Product product = productService.getProduct(reservation.getProductId());
        conversationService.publishStateToProductConversations(product);
        return ReservationResponse.of(reservation);
    }

    @Override
    @Transactional
    public void fulfillOnPayment(Product product, String buyerId, Long orderId, Instant now) {
        List<Reservation> active = reservationRepository.findByProductIdAndBuyerIdAndStatus(
                product.getProductId(), buyerId, ReservationStatus.ACTIVE);
        for (Reservation reservation : active) {
            // 만료 시각이 지난 예약은 예약으로 인정하지 않는다. 만료 자동 해제가 EXPIRED 로 바꾼다.
            if (!reservation.isActiveAt(now)) {
                continue;
            }
            // 보통 하나이고, 경합으로 둘 이상 생겼다면 모두 끝낸다.
            reservation.fulfill(orderId, now);
            logger.info("[예약 결제 종료] reservationId={}, orderId={}, memberId={}",
                    reservation.getReservationId(), orderId, buyerId);
        }
    }

    /*
     * 처리 순서(가격 제안 수량 설계안 5.5): 취소한 주문으로 결제 종료된 예약 조회 → 예약마다 같은 상품·대화·구매 희망자·판매자·
     * 수량·제안 ID 로 새 행 저장(예약 시각 now, 만료 시각 now + 예약 기간).
     * 등록 가격 결제는 합의가 예약 중인 동안 거절되므로, 결제 종료된 예약이 있는 주문은 합의 가격 주문뿐이다.
     * 수량 상한 검사는 하지 않는다. 취소로 늘어난 남은 수량과 새 예약의 수량이 같아 다른 회원의 구매 가능 수량이 취소 전과 같기 때문이다.
     * 상태 이벤트는 호출하는 주문 취소가 상품의 모든 대화에 보낸다.
     */
    @Override
    @Transactional
    public void reserveAgainOnCancel(Long orderId, Instant now) {
        List<Reservation> fulfilled = reservationRepository.findByFulfilledOrderIdAndStatus(orderId, ReservationStatus.FULFILLED);
        // 보통 하나이고, 경합으로 둘 이상이면 각각 새 행을 만든다. 결제 종료된 행은 바꾸지 않는다.
        for (Reservation previous : fulfilled) {
            Reservation reservation = reservationRepository.save(Reservation.reserve(previous.getProductId(),
                    previous.getConversationId(), previous.getBuyerId(), previous.getSellerId(), previous.getOfferId(),
                    previous.getQuantity(), now, now.plus(reservationDuration)));
            logger.info("[취소로 예약 생성] reservationId={}, previousReservationId={}, orderId={}, memberId={}, quantity={}, expiresAt={}",
                    reservation.getReservationId(), previous.getReservationId(), orderId, reservation.getBuyerId(),
                    reservation.getQuantity(), reservation.getExpiresAt());
        }
    }

    /**
     * 만료 시각이 지난 활성 예약을 찾아 기간 만료로 바꾼다.
     *
     * 처리 흐름:
     *   1. 상태가 활성이고 만료 시각이 {@code now} 이하인 예약의 ID 목록을 조회한다. 이 조회는 트랜잭션 밖에서 실행한다.
     *   2. 예약 ID 마다 {@link TransactionTemplate}으로 새 트랜잭션을 열고 {@link #expireIfStillDue}를 실행한다.
     *   3. 한 건에서 예외가 발생하면 그 건의 트랜잭션만 롤백하고 오류 로그를 남긴 뒤 다음 건을 처리한다.
     *   4. 대상 건수와 처리 건수를 로그로 남긴다.
     *
     * 이 메서드에는 {@code @Transactional}이 없다. 그래서 2번의 트랜잭션은 예약마다 독립적으로 커밋되거나 롤백된다.
     * 실패한 예약은 상태가 바뀌지 않으므로 다음 실행에서 다시 대상이 된다. 여러 인스턴스에서 동시에 실행되는 경우의 제어는 구현되어 있지 않다.
     *
     * @param now 대상 조회와 상태 재확인에 사용할 현재 시각. {@code TradeScheduler.runJob}이 전달한다.
     * @return 이번 실행에서 만료 처리한 예약 수
     */
    @Override
    public int expireReservations(Instant now) {
        List<Long> targets = reservationRepository.findIdsByExpired(ReservationStatus.ACTIVE, now);
        int processed = 0;
        for (Long reservationId : targets) {
            try {
                Boolean expired = transactionTemplate.execute(status -> expireIfStillDue(reservationId, now));
                if (Boolean.TRUE.equals(expired)) {
                    processed++;
                }
            } catch (RuntimeException e) {
                logger.error("[예약 만료 해제] 처리 실패 reservationId={}, reason={}", reservationId, e.getMessage());
            }
        }
        logger.info("[예약 만료 해제] 대상={}, 처리={}", targets.size(), processed);
        return processed;
    }

    /**
     * 예약이 아직 만료 자동 해제 조건을 만족하는지 다시 확인하고, 만족하면 기간 만료로 바꾼다.
     *
     * 예약이 없거나, 활성이 아니거나(대상 조회 뒤에 해제·결제 종료된 경우), 현재 시각이 만료 시각 전이면 건너뛴다.
     * {@link #expireReservations}가 연 트랜잭션 안에서 실행한다. 상태 검사는 잠금 없이 조회한 값으로 한다.
     */
    private boolean expireIfStillDue(Long reservationId, Instant now) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || !reservation.isActive() || now.isBefore(reservation.getExpiresAt())) {
            return false;
        }
        reservation.expire(now);
        logger.info("[예약 만료 해제] reservationId={}, productId={}, memberId={}",
                reservationId, reservation.getProductId(), reservation.getBuyerId());
        Product product = productService.getProduct(reservation.getProductId());
        conversationService.publishStateToProductConversations(product);
        return true;
    }
}
