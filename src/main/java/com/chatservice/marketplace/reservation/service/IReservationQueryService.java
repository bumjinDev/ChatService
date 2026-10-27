package com.chatservice.marketplace.reservation.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.reservation.domain.Reservation;

/**
 * 예약을 읽기만 하는 서비스. 상품·대화 조회와 결제 검사가 구매 가능 수량을 구할 때 쓴다.
 * 예약을 바꾸는 서비스({@link IReservationService})와 분리해 상품·대화 서비스와의 순환 의존을 피한다.
 */
public interface IReservationQueryService {

    /**
     * 조회한 회원 기준 구매 가능 수량. 남은 수량에서 활성이고 만료 시각 전이며 다른 회원에게 걸린 예약의 수량 합계를 뺀 값이다.
     * {@code viewerId}가 null 이면 로그인하지 않은 조회로 보고 모든 활성 예약을 뺀다. 음수여도 0 으로 바꾸지 않고 그대로 돌려준다.
     */
    long availableQuantity(Product product, String viewerId);

    /** 공개 목록용. 상품 ID → 조회한 회원 기준 구매 가능 수량. 예약은 한 번에 조회한다. */
    Map<Long, Long> availableQuantities(List<Product> products, String viewerId);

    /** 대화 상세의 예약 표시. 이 상품·구매 희망자의 예약 가운데 가장 최근 것 하나. 없으면 빈 Optional. */
    Optional<Reservation> latestFor(Long productId, String buyerId);
}
