package com.chatservice.marketplace.offer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatservice.marketplace.offer.dto.OfferRequest;
import com.chatservice.marketplace.offer.dto.OfferResponse;
import com.chatservice.marketplace.offer.service.IOfferService;

import jakarta.validation.Valid;

/** 가격 제안과 판매자 응답(설계 5.2.11, 5.2.12). 제안은 개당 금액과 수량을 받고, 수락과 거절은 본문을 받지 않는다. */
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
                .body(offerService.propose(memberId, conversationId, request.amount(), request.quantity(),
                        request.requestId()));
    }

    /** 판매자 수락. 합의가 성립하고, 같은 트랜잭션에서 그 구매 희망자에게 제안 수량만큼 예약을 건다. 응답의 reservation 에 그 예약을 담는다. */
    @PostMapping("/offers/{offerId}/accept")
    public OfferResponse accept(@AuthenticationPrincipal String memberId, @PathVariable("offerId") Long offerId) {
        return offerService.respond(memberId, offerId, true);
    }

    @PostMapping("/offers/{offerId}/reject")
    public OfferResponse reject(@AuthenticationPrincipal String memberId, @PathVariable("offerId") Long offerId) {
        return offerService.respond(memberId, offerId, false);
    }
}
