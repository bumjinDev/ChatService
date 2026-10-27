package com.chatservice.marketplace.reservation.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.reservation.ReservationRepository;
import com.chatservice.marketplace.reservation.domain.Reservation;
import com.chatservice.marketplace.reservation.domain.ReservationStatus;

/**
 * 구매 가능 수량 계산과 대화의 최근 예약 조회. 어떤 상태도 바꾸지 않는다.
 *
 * 구매 가능 수량 = 남은 수량 − (상품의 예약 가운데 상태가 ACTIVE 이고 만료 시각이 처리 기준 시각보다 뒤이며
 * 구매 희망자가 조회한 회원이 아닌 것의 수량 합계). 만료 시각이 지났지만 자동 해제가 아직 실행되지 않은 예약은 빼지 않는다.
 * 계산 결과가 음수이면 그대로 돌려준다. 경합으로 어긋난 값을 가리지 않기 위해서다.
 */
@Service
public class ReservationQueryService implements IReservationQueryService {

    private final ReservationRepository reservationRepository;
    private final TimeRules timeRules;

    public ReservationQueryService(ReservationRepository reservationRepository, TimeRules timeRules) {
        this.reservationRepository = reservationRepository;
        this.timeRules = timeRules;
    }

    @Override
    @Transactional(readOnly = true)
    public long availableQuantity(Product product, String viewerId) {
        Instant now = timeRules.now();
        long reserved = reservationRepository.findByProductIdAndStatus(product.getProductId(), ReservationStatus.ACTIVE).stream()
                .filter(reservation -> blocksViewer(reservation, viewerId, now))
                .mapToLong(Reservation::getQuantity)
                .sum();
        return product.getRemainingQuantity() - reserved;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> availableQuantities(List<Product> products, String viewerId) {
        Map<Long, Long> reservedByProduct = new HashMap<>();
        if (!products.isEmpty()) {
            Instant now = timeRules.now();
            List<Long> productIds = products.stream().map(Product::getProductId).toList();
            for (Reservation reservation : reservationRepository.findByProductIdInAndStatus(productIds, ReservationStatus.ACTIVE)) {
                if (blocksViewer(reservation, viewerId, now)) {
                    reservedByProduct.merge(reservation.getProductId(), reservation.getQuantity(), Long::sum);
                }
            }
        }
        Map<Long, Long> result = new HashMap<>();
        for (Product product : products) {
            result.put(product.getProductId(),
                    product.getRemainingQuantity() - reservedByProduct.getOrDefault(product.getProductId(), 0L));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> latestFor(Long productId, String buyerId) {
        return reservationRepository.findFirstByProductIdAndBuyerIdOrderByReservationIdDesc(productId, buyerId);
    }

    /** 활성이고 만료 시각 전이며 조회한 회원이 아닌 구매 희망자의 예약만 구매 가능 수량에서 뺀다. */
    private static boolean blocksViewer(Reservation reservation, String viewerId, Instant now) {
        return reservation.isActiveAt(now) && !reservation.getBuyerId().equals(viewerId);
    }
}
