package com.chatservice.marketplace.testsupport;

import java.time.Instant;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.order.service.IShipmentService;
import com.chatservice.marketplace.order.service.ITradeCompletionService;

/**
 * 자동 처리를 지정한 시각으로 한 번 실행하는 테스트 지원 API.
 *
 * 스케줄러({@code TradeScheduler})가 30초마다 부르는 자동 처리 메서드를 같은 방식으로 한 번 부른다.
 * 다른 점은 현재 시각 대신 요청 파라미터 {@code now}로 받은 시각을 넘긴다는 것이다. 그래서 상품 확인 기한(배송 완료 48시간 뒤)처럼
 * 실제로 기다려야 하는 기한을 기다리지 않고 지난 것으로 처리할 수 있다.
 *
 * 테스트 설정({@code marketplace.test-support.enabled})이 켜져 있을 때만 등록된다. {@code /test-support/**}는 어느 보안 필터 체인에도
 * 속하지 않아 인증 없이 호출된다. 자동 처리는 조건에 맞는 모든 주문을 처리하므로, 테스트 데이터만 있는 DB 계정에서 쓴다.
 */
// REST API 컨트롤러로 등록한다. 서버의 실제 주소는 context-path를 붙인 /ChatService/test-support/jobs다.
@RestController
@RequestMapping("/test-support/jobs")
// 테스트 설정이 true일 때만 이 컨트롤러를 등록한다. 설정이 꺼져 있으면 이 주소의 요청은 404가 된다.
@ConditionalOnProperty(name = "marketplace.test-support.enabled", havingValue = "true")
public class TestSupportJobController {

    // 실행한 자동 처리와 넘긴 시각, 처리 건수를 서버 로그에 남기는 로거.
    private static final Logger logger = LoggerFactory.getLogger(TestSupportJobController.class);

    private final IShipmentService shipmentService;
    private final ITradeCompletionService tradeCompletionService;
    private final TimeRules timeRules;

    public TestSupportJobController(IShipmentService shipmentService, ITradeCompletionService tradeCompletionService,
                                    TimeRules timeRules) {
        this.shipmentService = shipmentService;
        this.tradeCompletionService = tradeCompletionService;
        this.timeRules = timeRules;
    }

    /**
     * 모의 배송 완료를 한 번 실행한다.
     *
     * 모의 배송 완료 예정 시각이 {@code now} 이하인 진행 중·배송 중 주문을 배송 완료로 바꾸고,
     * {@code now}를 배송 완료 시각으로, 그 48시간 뒤를 상품 확인 기한으로 저장한다.
     *
     * @param now 처리 기준 시각(UTC, 예: 2026-10-21T12:00:00Z). 생략하면 서버의 현재 시각을 쓴다.
     * @return 예) {@code {"processed": 1}}
     */
    // POST /test-support/jobs/delivery-complete 요청을 처리한다.
    @PostMapping("/delivery-complete")
    public Map<String, Integer> completeDueDeliveries(@RequestParam(name = "now", required = false) Instant now) {
        return run("모의 배송 완료", now, shipmentService::completeDueDeliveries);
    }

    /**
     * 확인 기간 만료 자동 완료를 한 번 실행한다.
     *
     * 상품 확인 기한이 {@code now} 이하이고 환불 요청이 없는 진행 중·배송 완료 주문을 정상 완료로 바꾸고, 판매자에게 판매대금을 지급한다.
     * 주문의 종료 시각과 판매대금 지급 내역의 기록 시각에는 {@code now}가 저장된다.
     *
     * @param now 처리 기준 시각(UTC). 생략하면 서버의 현재 시각을 쓴다.
     * @return 예) {@code {"processed": 1}}
     */
    // POST /test-support/jobs/inspection-complete 요청을 처리한다.
    @PostMapping("/inspection-complete")
    public Map<String, Integer> completeExpiredInspections(@RequestParam(name = "now", required = false) Instant now) {
        return run("확인 기간 만료 자동 완료", now, tradeCompletionService::completeExpiredInspections);
    }

    private Map<String, Integer> run(String name, Instant now, ToIntFunction<Instant> job) {
        // now를 생략하면 스케줄러와 같이 서버의 현재 시각을 쓴다.
        Instant at = now != null ? now : timeRules.now();
        // 스케줄러가 부르는 것과 같은 자동 처리 메서드를 한 번 실행한다. 주문마다 새 트랜잭션에서 처리한다.
        int processed = job.applyAsInt(at);
        logger.info("[테스트 자동 처리 실행] {} now={}, processed={}", name, at, processed);
        return Map.of("processed", processed);
    }
}
