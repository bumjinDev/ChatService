package com.chatservice.marketplace.order.scheduler;

import java.time.Instant;
import java.util.function.ToIntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.order.service.IOrderCancellationService;
import com.chatservice.marketplace.order.service.IRefundService;
import com.chatservice.marketplace.order.service.IShipmentService;
import com.chatservice.marketplace.order.service.ITradeCompletionService;

/**
 * 거래 자동 처리 네 가지를 일정 간격으로 실행하는 스케줄러.
 *
 * 대상 주문의 선택과 상태 변경은 각 서비스의 자동 처리 메서드가 한다. 이 클래스는 실행 순서와 실행 간격만 정한다.
 *
 * - 각 자동 처리는 DB에 저장된 상태와 기준 시각으로 대상을 조회한다. 그래서 애플리케이션을 재시작해도 같은 대상을 처리한다.
 * - 한 작업이 예외로 끝나도 다음 작업은 실행한다.
 * - {@code fixedDelay}는 이전 실행이 끝난 뒤 설정한 간격이 지나면 다음 실행을 시작한다. 그래서 한 인스턴스 안에서는 실행이 겹치지 않는다.
 * - 여러 인스턴스에서 동시에 실행되는 경우의 제어는 구현되어 있지 않다.
 * - {@code marketplace.scheduler.enabled}가 {@code false}이면 빈을 등록하지 않는다. 통합 테스트 설정(application-integration.yml)은 이 값을 {@code false}로 두고 서비스 메서드를 직접 호출한다.
 */
@Component
@ConditionalOnProperty(name = "marketplace.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class TradeScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TradeScheduler.class);

    private final IOrderCancellationService cancellationService;
    private final IShipmentService shipmentService;
    private final ITradeCompletionService tradeCompletionService;
    private final IRefundService refundService;
    private final TimeRules timeRules;

    public TradeScheduler(IOrderCancellationService cancellationService, IShipmentService shipmentService,
                          ITradeCompletionService tradeCompletionService, IRefundService refundService,
                          TimeRules timeRules) {
        this.cancellationService = cancellationService;
        this.shipmentService = shipmentService;
        this.tradeCompletionService = tradeCompletionService;
        this.refundService = refundService;
        this.timeRules = timeRules;
    }

    /**
     * 자동 처리 네 가지를 정해진 순서로 한 번씩 실행한다.
     *
     * 실행 간격은 {@code marketplace.scheduler.fixed-delay}로 정한다. application.yml의 설정값은 30000ms다.
     * 실행 순서:
     *   1. 미발송 자동 취소
     *   2. 모의 배송 완료
     *   3. 확인 기간 만료 자동 완료
     *   4. 무응답 자동 환불
     * 각 작업은 {@link #runJob}이 실행하며, 작업마다 현재 시각을 새로 구한다.
     */
    @Scheduled(fixedDelayString = "${marketplace.scheduler.fixed-delay}")
    public void run() {
        // 1. 미발송 자동 취소. 발송 기한이 지나도록 발송 정보가 없는 주문을 취소하고 구매자에게 결제 금액을 반환한다.
        //    호출 흐름: runJob() → OrderCancellationService.cancelExpiredUnshipped() → PurchaseOrderRepository.findIdsByDeadlinePassed() → cancelIfStillExpired() → cancel()
        runJob("미발송 자동 취소", cancellationService::cancelExpiredUnshipped);
        // 2. 모의 배송 완료. 모의 배송 완료 예정 시각이 지난 배송 중 주문을 배송 완료로 바꾸고 상품 확인 기간을 시작한다.
        //    호출 흐름: runJob() → ShipmentService.completeDueDeliveries() → PurchaseOrderRepository.findIdsByDeliveryDue() → deliverIfDue() → PurchaseOrder.markDelivered()
        runJob("모의 배송 완료", shipmentService::completeDueDeliveries);
        // 3. 확인 기간 만료 자동 완료. 상품 확인 기한이 지나도록 구매 확정과 환불 요청이 없는 주문을 정상 완료하고 판매자에게 판매대금을 지급한다.
        //    호출 흐름: runJob() → TradeCompletionService.completeExpiredInspections() → PurchaseOrderRepository.findIdsByInspectionExpired() → completeIfExpired() → complete()
        runJob("확인 기간 만료 자동 완료", tradeCompletionService::completeExpiredInspections);
        // 4. 무응답 자동 환불. 판매자가 응답 기한까지 응답하지 않은 환불 요청을 자동 승인하고 구매자에게 결제 금액 전액을 반환한다.
        //    호출 흐름: runJob() → RefundService.autoApproveExpired() → RefundRequestRepository.findIdsByResponseExpired() → autoApproveIfExpired() → decide() → refund()
        runJob("무응답 자동 환불", refundService::autoApproveExpired);
    }

    /**
     * 자동 처리 작업 하나를 실행하고, 실패하면 오류 로그만 남긴다.
     *
     * 처리 흐름:
     *   1. 현재 시각을 구해 작업에 전달한다. 작업은 이 시각 하나를 대상 조회와 상태 재확인에 함께 사용한다.
     *   2. 작업이 {@code RuntimeException}을 던지면 오류 로그를 남기고 반환한다. 예외를 다시 던지지 않으므로 {@link #run}은 다음 작업을 계속 실행한다.
     * 작업이 반환한 처리 건수는 사용하지 않는다.
     *
     * @param name 로그에 남길 작업 이름
     * @param job  현재 시각을 받아 처리 건수를 반환하는 자동 처리 메서드
     */
    private void runJob(String name, ToIntFunction<Instant> job) {
        try {
            // 호출 흐름: TimeRules.now() → job.applyAsInt()
            job.applyAsInt(timeRules.now());
        } catch (RuntimeException e) {
            logger.error("[자동 처리] {} 실행 실패: {}", name, e.getMessage(), e);
        }
    }
}
