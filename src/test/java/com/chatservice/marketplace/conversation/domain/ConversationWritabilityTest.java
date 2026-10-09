package com.chatservice.marketplace.conversation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.chatservice.marketplace.conversation.domain.ConversationWritability.ReadOnlyReason;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;

/** BR-007 쓰기 가능 판단 규칙(설계 6.6). 여러 주문 조합을 순수 함수로 확인한다. */
class ConversationWritabilityTest {

    @Test
    void 판매_중이면_주문과_관계없이_쓰기_가능하다() {
        assertThat(ConversationWritability.evaluate(true, List.of()).writable()).isTrue();
        assertThat(ConversationWritability.evaluate(true, List.of(TradeStatus.CANCELLED)).writable()).isTrue();
    }

    @Test
    void 판매_종료이고_해당_구매자의_주문이_없으면_NO_ACTIVE_ORDER_로_읽기_전용이다() {
        ConversationWritability result = ConversationWritability.evaluate(false, List.of());
        assertThat(result.writable()).isFalse();
        assertThat(result.readOnlyReason()).isEqualTo(ReadOnlyReason.NO_ACTIVE_ORDER);
    }

    @Test
    void 판매_종료여도_진행_중이나_보류인_주문이_하나라도_있으면_쓰기_가능하다() {
        assertThat(ConversationWritability.evaluate(false,
                List.of(TradeStatus.COMPLETED, TradeStatus.IN_PROGRESS)).writable()).isTrue();
        assertThat(ConversationWritability.evaluate(false,
                List.of(TradeStatus.CANCELLED, TradeStatus.ON_HOLD)).writable()).isTrue();
    }

    @Test
    void 판매_종료이고_모든_주문이_최종_상태면_TRADE_FINALIZED_로_읽기_전용이다() {
        ConversationWritability result = ConversationWritability.evaluate(false,
                List.of(TradeStatus.COMPLETED, TradeStatus.REFUNDED, TradeStatus.CANCELLED));
        assertThat(result.writable()).isFalse();
        assertThat(result.readOnlyReason()).isEqualTo(ReadOnlyReason.TRADE_FINALIZED);
    }
}
