package com.chatservice.marketplace.order.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.order.service.IOrderCancellationService;
import com.chatservice.marketplace.order.service.IRefundService;
import com.chatservice.marketplace.order.service.IShipmentService;
import com.chatservice.marketplace.order.service.ITradeCompletionService;

/** 스케줄러는 네 자동 처리를 현재 시각으로 차례로 호출하고, 한 작업이 실패해도 나머지를 실행한다(설계 7.1). */
class TradeSchedulerTest {

    private final Instant now = Instant.parse("2026-10-13T00:00:00Z");
    private final IOrderCancellationService cancellation = mock(IOrderCancellationService.class);
    private final IShipmentService shipment = mock(IShipmentService.class);
    private final ITradeCompletionService completion = mock(ITradeCompletionService.class);
    private final IRefundService refund = mock(IRefundService.class);
    private final TradeScheduler scheduler = new TradeScheduler(cancellation, shipment, completion, refund,
            new TimeRules(Clock.fixed(now, ZoneOffset.UTC)));

    @Test
    void 네_작업을_현재_시각으로_순서대로_실행한다() {
        scheduler.run();
        InOrder order = inOrder(cancellation, shipment, completion, refund);
        order.verify(cancellation).cancelExpiredUnshipped(now);
        order.verify(shipment).completeDueDeliveries(now);
        order.verify(completion).completeExpiredInspections(now);
        order.verify(refund).autoApproveExpired(now);
    }

    @Test
    void 한_작업이_예외로_끝나도_다음_작업을_실행한다() {
        when(cancellation.cancelExpiredUnshipped(any())).thenThrow(new IllegalStateException("db down"));
        when(completion.completeExpiredInspections(any())).thenThrow(new IllegalStateException("db down"));
        scheduler.run();
        InOrder order = inOrder(shipment, refund);
        order.verify(shipment).completeDueDeliveries(now);
        order.verify(refund).autoApproveExpired(now);
    }
}
