package com.chatservice.marketplace.offer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatservice.marketplace.common.error.BusinessException;
import com.chatservice.marketplace.offer.dto.AcceptOfferRequest;
import com.chatservice.marketplace.offer.dto.OfferRequest;
import com.chatservice.marketplace.offer.dto.OfferResponse;
import com.chatservice.marketplace.offer.service.IOfferService;

import jakarta.validation.Valid;

/** 가격 제안과 판매자 응답(설계 5.2.11, 5.2.12). 수락은 본문으로 「예약도 함께」를 받을 수 있다. */
@RestController
@RequestMapping("/api")
public class OfferController {

    private final IOfferService offerService;

    public OfferController(IOfferService offerService) {
        this.offerService = offerService;
    }

    @PostMapping("/conversations/{conversationId}/offers")
    public ResponseEntity<OfferResponse> propose(@AuthenticationPrincipal String memberId,
                                                 @PathVariable("conversationId") Long conversationId,
                                                 @Valid @RequestBody OfferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(offerService.propose(memberId, conversationId, request.amount(), request.requestId()));
    }

    /**
     * 판매자 수락. 본문은 선택이며 없으면 수락만 한다.
     * {@code {"reserve": true, "quantity": n}}이면 수락과 함께 같은 트랜잭션에서 그 구매 희망자에게 n 개를 예약한다.
     * reserve 가 true 인데 quantity 가 없거나 1 미만이면 400 VALIDATION_ERROR 로 거절한다(본문에 따라 달라지는 입력 검사라 여기서 한다).
     */
    @PostMapping("/offers/{offerId}/accept")
    public OfferResponse accept(@AuthenticationPrincipal String memberId, @PathVariable("offerId") Long offerId,
                                @Valid @RequestBody(required = false) AcceptOfferRequest request) {
        Long reserveQuantity = null;
        if (request != null && request.wantsReservation()) {
            if (request.quantity() == null || request.quantity() < 1) {
                throw BusinessException.invalidField("quantity", "예약 수량은 1 이상의 정수여야 합니다.");
            }
            reserveQuantity = request.quantity();
        }
        return offerService.respond(memberId, offerId, true, reserveQuantity);
    }

    @PostMapping("/offers/{offerId}/reject")
    public OfferResponse reject(@AuthenticationPrincipal String memberId, @PathVariable("offerId") Long offerId) {
        return offerService.respond(memberId, offerId, false, null);
    }
}
