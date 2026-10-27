package com.chatservice.marketplace.offer.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.offer.domain.AgreementStatus;
import com.chatservice.marketplace.offer.domain.PriceOffer;
import com.chatservice.marketplace.order.PurchaseOrderRepository;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.PurchaseOrder;
import com.chatservice.marketplace.reservation.ReservationRepository;
import com.chatservice.marketplace.reservation.domain.Reservation;
import com.chatservice.marketplace.reservation.domain.ReservationStatus;

/**
 * 합의 상태 계산. 어떤 상태도 바꾸지 않는다.
 *
 * 예약 행은 RESERVATION.OFFER_ID, 주문 행은 PURCHASE_ORDER.OFFER_ID 로 제안과 연결한다. 모든 조회는 잠금 없이 하며,
 * 판단은 처리 기준 시각({@link TimeRules#now()})으로 한다. 만료 시각이 지났지만 자동 해제가 아직 실행되지 않은 예약은 활성으로 보지 않는다.
 */
@Service
public class AgreementQueryService implements IAgreementQueryService {

    private final ReservationRepository reservationRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final TimeRules timeRules;

    public AgreementQueryService(ReservationRepository reservationRepository,
                                 PurchaseOrderRepository purchaseOrderRepository, TimeRules timeRules) {
        this.reservationRepository = reservationRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.timeRules = timeRules;
    }

    @Override
    @Transactional(readOnly = true)
    public AgreementStatus statusOf(PriceOffer offer) {
        if (!offer.isAccepted()) {
            return null;
        }
        Instant now = timeRules.now();
        List<Reservation> reservations = reservationRepository.findByOfferId(offer.getOfferId());
        // 1. 그 제안의 예약 가운데 활성이고 만료 시각 전인 것이 있으면 예약 중
        if (reservations.stream().anyMatch(reservation -> reservation.isActiveAt(now))) {
            return AgreementStatus.RESERVED;
        }
        List<PurchaseOrder> orders = purchaseOrderRepository.findByOfferId(offer.getOfferId());
        // 2. 그 제안으로 만든 주문 가운데 진행 중이거나 보류인 것이 있으면 결제됨
        if (orders.stream().anyMatch(order -> order.getTradeStatus() == TradeStatus.IN_PROGRESS
                || order.getTradeStatus() == TradeStatus.ON_HOLD)) {
            return AgreementStatus.ORDERED;
        }
        // 3. 그 제안으로 만든 주문 가운데 정상 완료이거나 환불 완료인 것이 있으면 만료
        if (orders.stream().anyMatch(order -> order.getTradeStatus() == TradeStatus.COMPLETED
                || order.getTradeStatus() == TradeStatus.REFUNDED)) {
            return AgreementStatus.EXPIRED;
        }
        // 4. 그 제안의 예약 가운데 예약 ID 가 가장 큰 것이 해제이면 해제
        boolean released = reservations.stream()
                .max(Comparator.comparing(Reservation::getReservationId))
                .map(latest -> latest.getStatus() == ReservationStatus.RELEASED)
                .orElse(false);
        if (released) {
            return AgreementStatus.RELEASED;
        }
        // 5. 그 밖에는 만료. 예약 기간이 지난 활성 예약과 기간 만료 예약이 여기에 해당한다.
        return AgreementStatus.EXPIRED;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveReservation(Long productId, String buyerId) {
        Instant now = timeRules.now();
        return reservationRepository.findByProductIdAndBuyerIdAndStatus(productId, buyerId, ReservationStatus.ACTIVE)
                .stream().anyMatch(reservation -> reservation.isActiveAt(now));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveReservationForOffer(Long offerId) {
        Instant now = timeRules.now();
        return reservationRepository.findByOfferId(offerId).stream().anyMatch(reservation -> reservation.isActiveAt(now));
    }
}
