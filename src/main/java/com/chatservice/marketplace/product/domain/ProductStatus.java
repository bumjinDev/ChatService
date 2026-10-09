package com.chatservice.marketplace.product.domain;

/**
 * 상품의 판매 상태. PRODUCT.STATUS 컬럼에 문자열로 저장한다.
 *
 * 결제로 남은 수량이 0이 되면 {@code ON_SALE}에서 {@code SOLD}로 바뀐다.
 * 주문 취소와 환불 뒤에도 재고를 복구하지 않으므로 {@code SOLD}에서 {@code ON_SALE}로 돌아가지 않는다.
 *
 * @see Product
 */
public enum ProductStatus {
    /** 판매 중. 남은 수량이 1 이상이며 공개 목록과 상세 조회에 표시된다. */
    ON_SALE,
    /** 판매 종료. 남은 수량이 0이며 공개 목록과 상세 조회에서 제외된다. */
    SOLD
}
