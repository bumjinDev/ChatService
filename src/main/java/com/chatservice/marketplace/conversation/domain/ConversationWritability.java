package com.chatservice.marketplace.conversation.domain;

import java.util.Collection;

import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;

/**
 * 대화 쓰기 가능 여부(BR-007, 설계 6.6).
 *
 * 1. 상품이 판매 중이면 쓰기 가능하다.
 * 2. 판매 종료이면 이 대화의 상품·구매자·판매자가 모두 일치하는 주문만 본다(다른 구매자의 주문은 쓰지 않는다).
 *    진행 중 또는 보류인 주문이 하나라도 있으면 쓰기 가능하다.
 *    주문이 없으면 NO_ACTIVE_ORDER, 모두 최종 상태이면 TRADE_FINALIZED 로 읽기 전용이다.
 *
 * 상품 상태와 거래 상태는 한 방향으로만 바뀌고 재고 복구가 없으므로 읽기 전용이 다시 쓰기 가능으로 돌아가지 않는다.
 */
public record ConversationWritability(boolean writable, ReadOnlyReason readOnlyReason) {

    /** 대화가 읽기 전용인 이유. 대화 조회 응답의 {@code readOnlyReason} 필드로 전달한다. */
    public enum ReadOnlyReason {
        /** 상품이 판매 종료됐고, 이 대화의 구매자에게 주문이 없다. */
        NO_ACTIVE_ORDER,
        /** 상품이 판매 종료됐고, 이 대화의 구매자 주문이 모두 최종 상태다. */
        TRADE_FINALIZED
    }

    private static final ConversationWritability WRITABLE = new ConversationWritability(true, null);

    public static ConversationWritability evaluate(boolean productOnSale, Collection<TradeStatus> partyOrderStatuses) {
        if (productOnSale) {
            return WRITABLE;
        }
        if (partyOrderStatuses.isEmpty()) {
            return new ConversationWritability(false, ReadOnlyReason.NO_ACTIVE_ORDER);
        }
        boolean hasActiveOrder = partyOrderStatuses.stream().anyMatch(status -> !status.isFinal());
        return hasActiveOrder ? WRITABLE : new ConversationWritability(false, ReadOnlyReason.TRADE_FINALIZED);
    }
}
