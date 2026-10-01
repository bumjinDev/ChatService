package com.chatservice.marketplace.wallet;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** 잔액 API. 경로에 회원 ID 를 받지 않으므로 항상 요청한 회원 본인의 잔액만 다룬다. */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

	private final IWalletService walletService;

	public WalletController(IWalletService walletService) {
		this.walletService = walletService;
	}

	@PostMapping("/charges")
	public ResponseEntity<ChargeResponse> charge(@AuthenticationPrincipal String memberId,
			@Valid @RequestBody ChargeRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(walletService.charge(memberId, request));
	}
}
