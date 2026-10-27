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

/**
 * 예약 걸기, 해제, 결제 종료, 만료 자동 해제.
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

    public ReservationService(ReservationRepository reservationRepository,
                              IReservationQueryService reservationQueryService,
                              IConversationService conversationService, IProductService productService,
                              TransactionTemplate transactionTemplate, TimeRules timeRules,
                              @Value("${marketplace.reservation.duration}") Duration reservationDuration) {
        this.reservationRepository = reservationRepository;
        this.reservationQueryService = reservationQueryService;
        this.conversationService = conversationService;
        this.productService = productService;
        this.transactionTemplate = transactionTemplate;
        this.timeRules = timeRules;
        this.reservationDuration = reservationDuration;
    }

    /*
     * 처리 순서(설계안 5.2): 대화·참여자(404/403) → 판매자인지(403 NOT_SELLER) → 판매 중인지(409 PRODUCT_NOT_ON_SALE) →
     * 같은 구매 희망자에게 활성 예약이 없는지(409 RESERVATION_ALREADY_ACTIVE) →
     * 수량 ≤ 남은 수량 − 다른 회원의 활성 예약 합계(409 RESERVATION_QUANTITY_EXCEEDED) → 저장 → 상품의 모든 대화에 CONVERSATION_STATE(커밋 후).
     * 모든 검사는 변경 전에 끝나므로 거절된 요청은 아무것도 바꾸지 않는다.
     */
    @Override
    @Transactional
    public ReservationResponse reserve(String memberId, Long conversationId, long quantity) {
        // 1. 처리 기준 시각. 예약 시각과 만료 시각 계산에 같은 값을 쓴다.
        Instant now = timeRules.now();
        // 2. 대화 조회와 참여자 확인. 없으면 404, 참여자가 아니면 403 NOT_CONVERSATION_MEMBER.
        Conversation conversation = conversationService.getForMember(memberId, conversationId);
        // 3. 판매자 확인
        if (!conversation.getSellerId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOT_SELLER);
        }
        // 4. 상품 조회와 판매 중 확인
        Product product = productService.getProduct(conversation.getProductId());
        if (!product.isOnSale()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        // 5. 같은 구매 희망자의 활성 예약 확인. 만료 시각이 지난 예약은 자동 해제를 기다리는 것이라 새 예약을 막지 않는다.
        boolean activeExists = reservationRepository
                .findByProductIdAndBuyerIdAndStatus(product.getProductId(), conversation.getBuyerId(), ReservationStatus.ACTIVE)
                .stream().anyMatch(existing -> existing.isActiveAt(now));
        if (activeExists) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_ACTIVE);
        }
        // 6. 수량 상한 확인. 판매자는 어느 대화에서도 구매 희망자가 아니므로 판매자 기준 구매 가능 수량이 곧 더 걸 수 있는 예약 수량이다.
        long limit = reservationQueryService.availableQuantity(product, memberId);
        if (quantity > limit) {
            throw new BusinessException(ErrorCode.RESERVATION_QUANTITY_EXCEEDED);
        }
        // 7. 저장. IDENTITY 채번이라 저장 시점에 INSERT 된다.
        Reservation reservation = reservationRepository.save(Reservation.reserve(product.getProductId(), conversationId,
                conversation.getBuyerId(), memberId, quantity, now, now.plus(reservationDuration)));
        logger.info("[예약 걸기] reservationId={}, conversationId={}, productId={}, memberId={}, quantity={}, expiresAt={}",
                reservation.getReservationId(), conversationId, product.getProductId(), memberId, quantity,
                reservation.getExpiresAt());
        // 8. 상품의 모든 대화에 상태 이벤트 전송 등록. 다른 대화의 구매 가능 수량이 바뀌기 때문이다. 실제 전송은 커밋 후.
        conversationService.publishStateToProductConversations(product);
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
