package com.chatservice.marketplace.product.domain;

/**
 * 상품 대분류. PRODUCT.CATEGORY 컬럼에 문자열로 저장한다.
 *
 * DDL의 CHECK 제약도 아래 다섯 값만 허용한다. 하위 카테고리는 두지 않는다.
 *
 * @see Product
 */
public enum ProductCategory {
    /** 의류·잡화 */
    CLOTHING_ACCESSORIES,
    /** 생활용품 */
    LIVING,
    /** 전자기기 */
    ELECTRONICS,
    /** 도서·취미 */
    BOOKS_HOBBY,
    /** 기타 */
    ETC
}
