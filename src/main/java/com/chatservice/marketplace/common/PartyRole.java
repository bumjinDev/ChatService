package com.chatservice.marketplace.common;

/**
 * 대화·주문 조회에서 요청한 회원이 맡은 역할.
 *
 * 회원 종류가 아니다. 같은 회원이 상품과 거래마다 구매자가 되기도 하고 판매자가 되기도 한다.
 * 조회 응답의 {@code myRole} 필드로 전달한다.
 *
 * @see com.chatservice.marketplace.conversation.dto.ConversationDetailResponse
 * @see com.chatservice.marketplace.conversation.dto.ConversationSummaryResponse
 * @see com.chatservice.marketplace.order.dto.OrderDetailResponse
 */
public enum PartyRole {
    /** 구매자. 대화에서는 구매 희망자, 주문에서는 결제한 회원이다. */
    BUYER,
    /** 판매자. 상품을 등록한 회원이다. */
    SELLER
}
