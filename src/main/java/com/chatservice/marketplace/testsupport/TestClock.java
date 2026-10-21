package com.chatservice.marketplace.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 테스트 지원용 서버 시계.
 *
 * 테스트 설정({@code marketplace.test-support.enabled})이 켜져 있을 때만 등록되며, {@code ClockConfig}의 {@code Clock.systemUTC()} 대신
 * {@code TimeRules}에 주입된다({@code @Primary}). 서비스는 {@code TimeRules.now()}로 이 시계에서 현재 시각을 구한다.
 * - 고정하지 않았으면 시스템 시각(UTC)을 돌려준다.
 * - {@link #fix}로 고정하면 {@link #release}로 풀 때까지 고정한 시각을 그대로 돌려준다. 실제 시간이 지나도 바뀌지 않는다.
 * - 시계는 서버에 하나이므로 고정한 시각은 그 서버의 모든 요청에 적용된다. 설정은 메모리에만 있어 서버를 다시 시작하면 시스템 시각으로 돌아간다.
 * - 로그인 토큰의 만료와 서버 로그의 시각은 이 시계를 쓰지 않는다.
 */
// 테스트 설정이 true일 때만 빈으로 등록한다. Clock 빈이 둘이 되므로 @Primary로 이 시계가 주입되게 한다.
@Component
@Primary
@ConditionalOnProperty(name = "marketplace.test-support.enabled", havingValue = "true")
public class TestClock extends Clock {

    // 고정하지 않았을 때 돌려줄 시스템 시각의 출처. ClockConfig의 기본 시계와 같다.
    private final Clock system = Clock.systemUTC();

    // 고정한 시각. null이면 고정하지 않은 상태다. 테스트 지원 API를 처리하는 스레드가 쓰고 다른 요청의 스레드가 읽으므로 volatile로 둔다.
    private volatile Instant fixedInstant;

    /** 시각을 고정한다. */
    public void fix(Instant instant) {
        this.fixedInstant = instant;
    }

    /** 고정을 풀고 시스템 시각으로 되돌린다. */
    public void release() {
        this.fixedInstant = null;
    }

    /** 시각을 고정했으면 {@code true}. */
    public boolean isFixed() {
        return fixedInstant != null;
    }

    @Override
    public Instant instant() {
        // 한 번만 읽어 고정 여부 확인과 반환에 같은 값을 쓴다.
        Instant fixed = fixedInstant;
        return fixed != null ? fixed : system.instant();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        // TimeRules는 instant()만 쓰고 영업일 계산은 BUSINESS_ZONE으로 따로 한다. 테스트용 MutableClock과 같이 자신을 돌려준다.
        return this;
    }
}
