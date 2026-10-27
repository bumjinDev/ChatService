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
import com.chatservice.marketplace.reservation.service.IReservationService;

/**
 * 만료 예약 해제를 지정한 시각으로 한 번 실행하는 테스트 지원 API.
 *
 * 스케줄러({@code TradeScheduler})가 30초마다 부르는 {@code ReservationService.expireReservations}를 같은 방식으로 한 번 부른다.
 * 다른 점은 현재 시각 대신 요청 파라미터 {@code now}로 받은 시각을 넘긴다는 것이다. 그래서 예약 기간이 지나기를 기다리지 않고
 * 만료 뒤의 자동 해제를 실행할 수 있다.
 *
 * 테스트 설정({@code marketplace.test-support.enabled})이 켜져 있을 때만 등록된다. {@code /test-support/**}는 어느 보안 필터 체인에도
 * 속하지 않아 인증 없이 호출된다. 자동 해제는 조건에 맞는 모든 예약을 처리하므로, 테스트 데이터만 있는 DB 계정에서 쓴다.
 * 다른 자동 처리의 실행 API 는 {@link TestSupportJobController}와 {@link TestSupportUnshippedCancelController}에 있다.
 */
// REST API 컨트롤러로 등록한다. 서버의 실제 주소는 context-path를 붙인 /ChatService/test-support/jobs/reservation-release이다.
@RestController
@RequestMapping("/test-support/jobs")
// 테스트 설정이 true일 때만 이 컨트롤러를 등록한다. 설정이 꺼져 있으면 이 주소의 요청은 404가 된다.
@ConditionalOnProperty(name = "marketplace.test-support.enabled", havingValue = "true")
public class TestSupportReservationReleaseController {

    // 넘긴 시각과 처리 건수를 서버 로그에 남기는 로거. 다른 테스트 지원 실행 API 와 같은 형식으로 남긴다.
    private static final Logger logger = LoggerFactory.getLogger(TestSupportReservationReleaseController.class);

    private final IReservationService reservationService;
    private final TimeRules timeRules;

    public TestSupportReservationReleaseController(IReservationService reservationService, TimeRules timeRules) {
        this.reservationService = reservationService;
        this.timeRules = timeRules;
    }

    /**
     * 만료 예약 해제를 한 번 실행한다.
     *
     * 만료 시각이 {@code now} 이하인 활성 예약을 기간 만료로 바꾼다. 예약의 끝난 시각에는 {@code now}가 저장된다.
     *
     * @param now 처리 기준 시각(UTC, 예: 2026-10-27T12:20:00Z). 생략하면 서버의 처리 기준 시각을 쓴다.
     * @return 예) {@code {"processed": 1}}
     */
    // POST /test-support/jobs/reservation-release 요청을 처리한다.
    @PostMapping("/reservation-release")
    public Map<String, Integer> expireReservations(@RequestParam(name = "now", required = false) Instant now) {
        // now를 생략하면 스케줄러와 같이 처리 기준 시각을 쓴다. 처리 기준 시각을 고정했으면 고정한 시각이다.
        Instant at = now != null ? now : timeRules.now();
        // 스케줄러가 부르는 것과 같은 자동 처리 메서드를 한 번 실행한다. 예약마다 새 트랜잭션에서 처리하고 커밋한 뒤 반환한다.
        int processed = reservationService.expireReservations(at);
        logger.info("[테스트 자동 처리 실행] 만료 예약 해제 now={}, processed={}", at, processed);
        return Map.of("processed", processed);
    }
}
