package com.chatservice.marketplace.conversation.realtime.event;

/**
 * 상품 남은 수량 변경, 주문 생성·최종 상태 시 두 참여자 세션에 보내는 대화 상태(설계 5.3 CONVERSATION_STATE).
 * 다른 구매자의 주문 정보는 넣지 않는다.
 */
public record ConversationStateEvent(String type, Long conversationId, String productStatus, long remainingQuantity,
                                     boolean writable, String readOnlyReason) {

    public static ConversationStateEvent of(Long conversationId, String productStatus, long remainingQuantity,
                                            boolean writable, String readOnlyReason) {
        return new ConversationStateEvent("CONVERSATION_STATE", conversationId, productStatus, remainingQuantity,
                writable, readOnlyReason);
    }
}
