package com.chatservice.marketplace.product.dto;

import com.chatservice.marketplace.common.AmountRules;
import com.chatservice.marketplace.product.domain.ProductCategory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 상품 등록 요청(F-001, 설계 5.2.1). price 는 개당 가격, quantity 는 최초·남은 판매 수량의 시작값이다.
 * 소수는 JSON 을 읽는 단계에서 거절되고(accept-float-as-int=false), 상한은 DB 저장 범위다.
 */
public record ProductRegisterRequest(
        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "상품 설명은 필수입니다.")
        String description,

        @NotNull(message = "카테고리는 필수입니다.")
        ProductCategory category,

        @NotNull(message = "가격은 필수입니다.")
        @Min(value = 1, message = "가격은 0보다 큰 정수여야 합니다.")
        @Max(value = AmountRules.MAX_AMOUNT, message = "가격이 저장 가능한 범위를 초과합니다.")
        Long price,

        @NotNull(message = "판매 수량은 필수입니다.")
        @Min(value = 1, message = "판매 수량은 1 이상의 정수여야 합니다.")
        @Max(value = AmountRules.MAX_QUANTITY, message = "판매 수량이 저장 가능한 범위를 초과합니다.")
        Long quantity) {
}
