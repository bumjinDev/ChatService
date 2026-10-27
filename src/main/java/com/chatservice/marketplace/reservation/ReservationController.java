package com.chatservice.marketplace.reservation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatservice.marketplace.reservation.dto.ReservationResponse;
import com.chatservice.marketplace.reservation.service.IReservationService;

/**
 * 예약 해제. 예약은 판매자가 가격 제안을 수락할 때 함께 걸리므로 예약을 거는 요청은 따로 없다.
 * 모든 경로는 인증이 필요하고, 권한·상태 검사는 서비스가 한다.
 */
@RestController
@RequestMapping("/api")
public class ReservationController {

    private final IReservationService reservationService;

    public ReservationController(IReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** 예약의 판매자 또는 예약된 구매 희망자가 예약을 해제한다. 성공하면 200 과 상태가 RELEASED 인 예약 응답. */
    @DeleteMapping("/reservations/{reservationId}")
    public ReservationResponse release(@AuthenticationPrincipal String memberId,
                                       @PathVariable("reservationId") Long reservationId) {
        return reservationService.release(memberId, reservationId);
    }
}
