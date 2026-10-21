package com.chatservice.marketplace.testsupport;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatservice.marketplace.common.time.TimeRules;

/**
 * 서버 시계를 지정한 시각으로 고정하고, 고정을 풀고, 지금 설정을 조회하는 테스트 지원 API.
 *
 * 사용자 요청에는 처리 기준 시각을 넣을 수 없으므로, 기한 직전에 들어온 사용자 요청을 만들 때 이 API로 서버 시계를 고정한다.
 * 테스트 설정({@code marketplace.test-support.enabled})이 켜져 있을 때만 등록된다. {@code /test-support/**}는 어느 보안 필터 체인에도
 * 속하지 않아 인증 없이 호출된다. 고정한 시각은 서버의 모든 요청에 적용되므로 테스트가 끝나면 DELETE로 되돌린다.
 */
// REST API 컨트롤러로 등록한다. 서버의 실제 주소는 context-path를 붙인 /ChatService/test-support/clock이다.
@RestController
@RequestMapping("/test-support/clock")
@ConditionalOnProperty(name = "marketplace.test-support.enabled", havingValue = "true")
public class TestSupportClockController {

    // 시계를 고정하고 푼 기록을 서버 로그에 남긴다. 테스트 결과를 확인할 때 이 로그로 고정한 시각과 순서를 본다.
    private static final Logger logger = LoggerFactory.getLogger(TestSupportClockController.class);

    private final TestClock testClock;
    private final TimeRules timeRules;

    public TestSupportClockController(TestClock testClock, TimeRules timeRules) {
        this.testClock = testClock;
        this.timeRules = timeRules;
    }

    /**
     * 서버 시계를 본문의 시각으로 고정한다.
     *
     * @param request 예) {@code {"instant": "2026-10-12T14:59:59.500Z"}}
     * @return 고정한 뒤의 설정. 본문에 instant가 없으면 400
     */
    // PUT /test-support/clock 요청을 처리한다.
    @PutMapping
    public ResponseEntity<ClockStatus> fix(@RequestBody FixRequest request) {
        if (request.instant() == null) {
            return ResponseEntity.badRequest().build();
        }
        testClock.fix(request.instant());
        logger.info("[테스트 서버 시계] 고정 instant={}", request.instant());
        return ResponseEntity.ok(status());
    }

    /** 고정을 풀고 시스템 시각으로 되돌린다. */
    // DELETE /test-support/clock 요청을 처리한다.
    @DeleteMapping
    public ClockStatus release() {
        testClock.release();
        logger.info("[테스트 서버 시계] 고정 해제");
        return status();
    }

    /** 지금 서버가 현재 시각으로 쓰는 값과 고정 여부를 돌려준다. */
    // GET /test-support/clock 요청을 처리한다.
    @GetMapping
    public ClockStatus current() {
        return status();
    }

    private ClockStatus status() {
        // 서비스와 같은 방법(TimeRules.now(), 마이크로초 단위로 자름)으로 구한 값을 돌려준다.
        return new ClockStatus(timeRules.now(), testClock.isFixed());
    }

    /** PUT 요청 본문. */
    public record FixRequest(Instant instant) {
    }

    /** 응답 본문. 예) {@code {"instant": "2026-10-12T14:59:59.500Z", "fixed": true}} */
    public record ClockStatus(Instant instant, boolean fixed) {
    }
}
