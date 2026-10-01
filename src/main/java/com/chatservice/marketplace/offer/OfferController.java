package com.chatservice.marketplace.offer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** 가격 제안 API(설계 명세서 5.2.11, 5.2.12). */
@RestController
public class OfferController {

	private final IOfferService offerService;

	public OfferController(IOfferService offerService) {
		this.offerService = offerService;
	}

	@PostMapping("/api/conversations/{conversationId}/offers")
	public ResponseEntity<OfferResponse> propose(@AuthenticationPrincipal String memberId,
			@PathVariable("conversationId") Long conversationId, @Valid @RequestBody OfferProposeRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(offerService.propose(memberId, conversationId, request));
	}
}
