package com.chatservice.marketplace.reservation.service;

import java.time.Instant;

import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.reservation.dto.ReservationResponse;

public interface IReservationService {

    /** 판매자가 대화의 구매 희망자에게 예약을 건다. 가격 제안 수락과 함께 걸 때는 수락과 같은 트랜잭션에서 실행된다. */
    ReservationResponse reserve(String memberId, Long conversationId, long quantity);

    /** 판매자 또는 예약된 구매 희망자가 예약을 해제한다. */
    ReservationResponse release(String memberId, Long reservationId);

    /**
     * 결제 성공 시 결제한 회원의 활성 예약(만료 시각 전)을 결제 종료로 바꾼다. 호출하는 쪽의 트랜잭션에서 실행된다.
     * 만료 시각이 지난 예약은 예약으로 인정하지 않고 만료 자동 해제가 처리하도록 둔다.
     */
    void fulfillOnPayment(Product product, String buyerId, Long orderId, Instant now);

    /**
     * 만료 시각이 지난 활성 예약을 기간 만료로 바꾼다.
     *
     * {@code TradeScheduler}가 일정 간격으로 호출한다.
     *
     * @param now 대상 조회와 상태 재확인에 사용할 현재 시각
     * @return 이번 실행에서 만료 처리한 예약 수
     */
    int expireReservations(Instant now);
}
