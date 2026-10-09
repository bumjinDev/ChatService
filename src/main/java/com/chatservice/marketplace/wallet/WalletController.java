package com.chatservice.marketplace.wallet;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatservice.marketplace.wallet.dto.BalanceTransactionResponse;
import com.chatservice.marketplace.wallet.dto.ChargeRequest;
import com.chatservice.marketplace.wallet.dto.ChargeResponse;
import com.chatservice.marketplace.wallet.dto.WalletResponse;
import com.chatservice.marketplace.wallet.service.IWalletService;

import jakarta.validation.Valid;

/**
 * 테스트 잔액 충전·조회(설계 5.2.4, 5.2.5).
 * 경로에 회원 ID 를 받지 않으므로 항상 인증된 본인의 잔액만 다룬다(F-004-AC-02).
 */
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walletService.charge(memberId, request.amount(), request.requestId()));
    }

    @GetMapping
    public WalletResponse wallet(@AuthenticationPrincipal String memberId) {
        return walletService.getWallet(memberId);
    }

    @GetMapping("/transactions")
    public List<BalanceTransactionResponse> transactions(@AuthenticationPrincipal String memberId) {
        return walletService.getTransactions(memberId);
    }
}
