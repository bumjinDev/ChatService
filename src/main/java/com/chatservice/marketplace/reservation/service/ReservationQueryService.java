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
import com.chatservice.marketplace.testsupport.TradeTestLog;

/**
 * 구매 가능 수량 계산 및 대화의 최근 예약 조회. 어떤 상태도 바꾸지 않는다.
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

    /**
     * 조회하는 회원이 이 상품을 지금 몇 개까지 살 수 있는지(구매 가능 수량) 계산한다. 데이터는 바꾸지 않으며 잠금 없이 조회한 값으로 계산한다.
     *
     * 구매 가능 수량 = 상품의 남은 수량 − 이 상품에서 만료 전인 예약 가운데 조회하는 회원 본인에게 걸리지 않은 예약의 수량 합계
     * - 예약은 남은 수량을 줄이지 않으므로, 예약된 수량은 여기서 제외한다.
     * - 예약은 그 구매 희망자 몫으로 수량을 잡아 두는 것이라서 조회하는 회원 본인에게 걸린 예약은 빼지 않는다. 그래서 같은 상품이라도 조회하는 회원마다 값이 다르다
     * - 상태가 아직 ACTIVE 여도 만료 시각이 지난 예약은 빼지 않는다
     * - 경합으로 음수가 나와도 0 으로 바꾸지 않고 그대로 돌려준다. 어긋난 값을 가리지 않기 위해서다
     *
     * 구매 가능 수량은 정의가 하나이고 조회하는 회원마다 값이 다르므로, 계산을 이 메서드 한 곳에 두고 호출하는 쪽이 그때 요청한 회원의 ID 를 넘긴다.
     * 그래서 화면에 보이는 수량과 요청을 거절하는 기준이 같은 계산에서 나온다. 호출하는 곳은 다음과 같다.
     * - 상품 상세·대화 상세 조회, 대화 상태 이벤트 : 화면에 보여 줄 수량
     * - 가격 제안 등록, 결제 : 요청 수량이 이 값보다 많으면 거절한다
     * - 가격 제안 수락의 예약 생성 : 판매자 ID 를 넘긴다. 판매자 본인에게 걸린 예약은 없으므로 만료 전인 예약을 모두 남은 수량에서 뺀다
     *
     * @param product  수량을 계산할 상품
     * @param viewerId 조회하는 회원 ID. 로그인하지 않은 조회는 null 이며, 이때도 만료 전인 예약을 모두 남은 수량에서 뺀다
     * @return 구매 가능 수량
     */
    @Override
    @Transactional(readOnly = true)
    public long availableQuantity(Product product, String viewerId) {
        // 처리 기준 시각. 예약의 만료 여부를 이 시각으로 확인한다
        Instant now = timeRules.now();

        // 이 상품의 예약 가운데 상태가 예약 중(ACTIVE)인 예약을 모두 가져온다(테이블 "RESERVATION")
        long reserved = reservationRepository.findByProductIdAndStatus(product.getProductId(), ReservationStatus.ACTIVE).stream()
                // 조회하는 회원의 구매를 막는 예약만 고른다 : 만료 전이고, 조회하는 회원 본인에게 걸린 예약이 아닌 것
                .filter(reservation -> blocksViewer(reservation, viewerId, now))
                // 고른 예약마다 예약 수량을 꺼낸다
                .mapToLong(Reservation::getQuantity)
                // 꺼낸 수량을 모두 더한다
                .sum();

        // 남은 수량에서 더한 수량을 뺀 값이 구매 가능 수량이다
        long available = product.getRemainingQuantity() - reserved;
        TradeTestLog.rememberQuantityCalculation(product.getRemainingQuantity(), reserved, now);
        return available;
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

    /**
     * 이 예약이 조회하는 회원의 구매를 막는 예약인지 확인한다. true 이면 이 예약 수량을 구매 가능 수량 계산에서 남은 수량에서 뺀다.
     *
     * 다음 두 가지가 모두 맞으면 true 다.
     * - 예약 중(ACTIVE)이고 만료 시각이 처리 기준 시각보다 뒤다. 만료 시각이 지난 예약은 상태가 아직 ACTIVE 여도 구매를 막지 않는다
     * - 예약을 받은 구매 희망자가 조회하는 회원이 아니다. 조회하는 회원 본인에게 걸린 예약은 그 회원 몫이므로 그 회원의 구매를 막지 않는다
     *
     * 조회하는 회원이 판매자이면 둘째 조건은 어떤 예약에서도 맞는다. 판매자 본인에게 걸린 예약은 없기 때문이다.
     * 로그인하지 않은 조회(viewerId 가 null)도 같다. 그래서 두 때에는 만료 전인 예약이 모두 true 가 된다.
     *
     * @param reservation 확인할 예약
     * @param viewerId    조회하는 회원 ID. 가격 제안 수락에서는 판매자 ID, 로그인하지 않은 조회에서는 null
     * @param now         처리 기준 시각
     * @return 이 예약 수량을 남은 수량에서 빼야 하면 true
     */
    private static boolean blocksViewer(Reservation reservation, String viewerId, Instant now) {
        // 앞 조건 : 예약 중이고 만료 전인가, 뒤 조건 : 예약을 받은 구매 희망자가 조회하는 회원이 아닌가
        return reservation.isActiveAt(now) && !reservation.getBuyerId().equals(viewerId);
    }
}
