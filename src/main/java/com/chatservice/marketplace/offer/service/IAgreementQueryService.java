package com.chatservice.marketplace.offer.service;

import com.chatservice.marketplace.offer.domain.AgreementStatus;
import com.chatservice.marketplace.offer.domain.PriceOffer;

/**
 * 합의 상태를 읽기만 하는 서비스. 가격 제안, 결제, 대화 상세가 함께 쓴다.
 * 저장소와 시각 규칙에만 의존하므로 제안·주문·대화 서비스가 모두 참조해도 순환 의존이 생기지 않는다.
 */
public interface IAgreementQueryService {

    /**
     * 수락된 제안의 합의 상태. 응답 대기 제안과 거절된 제안이면 null 이다.
     *
     * 판단 순서: 그 제안의 예약 가운데 활성이고 만료 전인 것이 있으면 RESERVED →
     * 그 제안으로 만든 주문 가운데 진행 중·보류가 있으면 ORDERED → 정상 완료·환불 완료가 있으면 EXPIRED →
     * 그 제안의 예약 가운데 예약 ID 가 가장 큰 것이 해제이면 RELEASED → 그 밖에는 EXPIRED.
     */
    AgreementStatus statusOf(PriceOffer offer);

    /** 이 상품에서 이 구매 희망자에게 활성이고 만료 시각 전인 예약이 있는지. 등록 가격 결제를 거절하는 판단에 쓴다. */
    boolean hasActiveReservation(Long productId, String buyerId);

    /** 이 제안의 예약 가운데 활성이고 만료 시각 전인 것이 있는지. 합의 가격 결제를 거절하는 판단에 쓴다. */
    boolean hasActiveReservationForOffer(Long offerId);
}
