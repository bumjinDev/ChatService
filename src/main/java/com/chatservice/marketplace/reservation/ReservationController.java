package com.chatservice.marketplace.reservation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatservice.marketplace.reservation.dto.ReservationRequest;
import com.chatservice.marketplace.reservation.dto.ReservationResponse;
import com.chatservice.marketplace.reservation.service.IReservationService;

import jakarta.validation.Valid;

/** 판매자 예약 걸기와 해제. 모든 경로는 인증이 필요하고, 권한·상태 검사는 서비스가 한다. */
@RestController
@RequestMapping("/api")
public class ReservationController {

    private final IReservationService reservationService;

    public ReservationController(IReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** 대화의 판매자가 그 구매 희망자에게 예약을 건다. 성공하면 201 과 예약 응답. */
    @PostMapping("/conversations/{conversationId}/reservations")
    public ResponseEntity<ReservationResponse> reserve(@AuthenticationPrincipal String memberId,
                                                       @PathVariable("conversationId") Long conversationId,
                                                       @Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.reserve(memberId, conversationId, request.quantity()));
    }

    /** 예약의 판매자 또는 예약된 구매 희망자가 예약을 해제한다. 성공하면 200 과 상태가 RELEASED 인 예약 응답. */
    @DeleteMapping("/reservations/{reservationId}")
    public ReservationResponse release(@AuthenticationPrincipal String memberId,
                                       @PathVariable("reservationId") Long reservationId) {
        return reservationService.release(memberId, reservationId);
    }
}
