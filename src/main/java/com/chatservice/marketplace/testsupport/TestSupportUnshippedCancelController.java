package com.chatservice.marketplace.testsupport;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.order.service.IOrderCancellationService;

/**
 * 미발송 자동 취소를 지정한 시각으로 한 번 실행하는 테스트 지원 API.
 *
 * 스케줄러({@code TradeScheduler})가 30초마다 부르는 {@code OrderCancellationService.cancelExpiredUnshipped}를 같은 방식으로 한 번 부른다.
 * 다른 점은 현재 시각 대신 요청 파라미터 {@code now}로 받은 시각을 넘긴다는 것이다. 그래서 발송 기한이 지나기를 기다리지 않고
 * 기한이 지난 뒤의 자동 취소를 실행할 수 있다.
 * 자동 취소는 이 요청을 처리하는 서버 스레드에서 실행되므로, 요청 헤더로 중지 위치를 지정하면 공통 취소 처리의 WALLET_READ에서 멈출 수 있다.
 *
 * 테스트 설정({@code marketplace.test-support.enabled})이 켜져 있을 때만 등록된다. {@code /test-support/**}는 어느 보안 필터 체인에도
 * 속하지 않아 인증 없이 호출된다. 자동 취소는 조건에 맞는 모든 주문을 처리하므로, 테스트 데이터만 있는 DB 계정에서 쓴다.
 * 모의 배송 완료와 확인 기간 만료 자동 완료의 실행 API는 {@link TestSupportJobController}에 있다.
 */
// REST API 컨트롤러로 등록한다. 서버의 실제 주소는 context-path를 붙인 /ChatService/test-support/jobs/unshipped-cancel이다.
@RestController
@RequestMapping("/test-support/jobs")
// 테스트 설정이 true일 때만 이 컨트롤러를 등록한다. 설정이 꺼져 있으면 이 주소의 요청은 404가 된다.
@ConditionalOnProperty(name = "marketplace.test-support.enabled", havingValue = "true")
public class TestSupportUnshippedCancelController {

    // 넘긴 시각과 처리 건수를 서버 로그에 남기는 로거. TestSupportJobController와 같은 형식으로 남긴다.
    private static final Logger logger = LoggerFactory.getLogger(TestSupportUnshippedCancelController.class);

    private final IOrderCancellationService cancellationService;
    private final TimeRules timeRules;

    public TestSupportUnshippedCancelController(IOrderCancellationService cancellationService, TimeRules timeRules) {
        this.cancellationService = cancellationService;
        this.timeRules = timeRules;
    }

    /**
     * 미발송 자동 취소를 한 번 실행한다.
     *
     * 발송 기한이 {@code now} 이하인 진행 중·발송 대기 주문 가운데 발송 정보가 없는 주문을 취소하고, 구매자에게 결제 금액을 반환한다.
     * 주문의 종료 시각과 취소 반환 내역의 기록 시각에는 {@code now}가 저장된다.
     *
     * @param now 처리 기준 시각(UTC, 예: 2026-10-12T15:00:05Z). 생략하면 서버의 현재 시각을 쓴다.
     * @return 예) {@code {"processed": 1}}
     */
    // POST /test-support/jobs/unshipped-cancel 요청을 처리한다.
    @PostMapping("/unshipped-cancel")
    public Map<String, Integer> cancelExpiredUnshipped(@RequestParam(name = "now", required = false) Instant now) {
        // now를 생략하면 스케줄러와 같이 서버의 현재 시각을 쓴다. 서버 시계를 고정했으면 고정한 시각이다.
        Instant at = now != null ? now : timeRules.now();
        // 스케줄러가 부르는 것과 같은 자동 처리 메서드를 한 번 실행한다. 주문마다 새 트랜잭션에서 처리하고 커밋한 뒤 반환한다.
        int processed = cancellationService.cancelExpiredUnshipped(at);
        logger.info("[테스트 자동 처리 실행] 미발송 자동 취소 now={}, processed={}", at, processed);
        return Map.of("processed", processed);
    }
}
