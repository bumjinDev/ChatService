package com.chatservice.marketplace.order.dto;

import com.chatservice.marketplace.common.AmountRules;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 구매·잔액 결제 요청(F-010, 설계 5.2.13). 금액은 받지 않는다. 서버가 적용 단가 × quantity 로 계산한다.
 * offerId 가 없으면 등록 가격, 있으면 본인에게 수락된 합의 가격을 적용한다.
 */
public record PlaceOrderRequest(
        @NotNull(message = "상품은 필수입니다.")
        Long productId,

        @NotNull(message = "구매 수량은 필수입니다.")
        @Min(value = 1, message = "구매 수량은 1 이상의 정수여야 합니다.")
        @Max(value = AmountRules.MAX_QUANTITY, message = "구매 수량이 저장 가능한 범위를 초과합니다.")
        Long quantity,

        @NotBlank(message = "수령인 이름은 필수입니다.")
        @Size(max = 100, message = "수령인 이름은 100자 이하여야 합니다.")
        String recipientName,

        @NotBlank(message = "배송 주소는 필수입니다.")
        @Size(max = 500, message = "배송 주소는 500자 이하여야 합니다.")
        String shippingAddress,

        Long offerId,

        @Size(max = 64, message = "requestId 는 64자 이하여야 합니다.")
        String requestId) {
}
