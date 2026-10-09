package com.chatservice.marketplace.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** BR-006 발송 기한(5영업일)과 48시간 기한 계산. 설계 명세서 9장 "BR-006 영업일" 항목. */
class TimeRulesTest {

    private final TimeRules timeRules = new TimeRules(Clock.fixed(Instant.parse("2026-10-05T01:00:00Z"), ZoneOffset.UTC));

    private static Instant kst(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(TimeRules.BUSINESS_ZONE).toInstant();
    }

    @Test
    void 월요일_확정은_다음주_화요일_0시_KST에_기한이_지난다() {
        // 2026-10-05 은 월요일이다. 화(1) 수(2) 목(3) 금(4) 월(5) → 화요일 00:00
        assertThat(timeRules.shipDeadlineAt(kst("2026-10-05T10:00:00"))).isEqualTo(kst("2026-10-13T00:00:00"));
    }

    @Test
    void 금요일_확정은_다음주_토요일_0시_KST에_기한이_지난다() {
        assertThat(timeRules.shipDeadlineAt(kst("2026-10-09T23:59:59"))).isEqualTo(kst("2026-10-17T00:00:00"));
    }

    @Test
    void 토요일_확정도_다음주_토요일_0시_KST에_기한이_지난다() {
        assertThat(timeRules.shipDeadlineAt(kst("2026-10-10T09:00:00"))).isEqualTo(kst("2026-10-17T00:00:00"));
    }

    @Test
    void 일요일_확정은_월요일부터_세어_다음주_토요일_0시_KST이다() {
        assertThat(timeRules.shipDeadlineAt(kst("2026-10-11T12:00:00"))).isEqualTo(kst("2026-10-17T00:00:00"));
    }

    @Test
    void 확정일은_UTC가_아니라_KST_날짜로_판단한다() {
        // UTC 일요일 16:00 은 KST 월요일 01:00 이므로 월요일 확정으로 계산한다.
        Instant sundayUtcMondayKst = Instant.parse("2026-10-04T16:00:00Z");
        assertThat(timeRules.shipDeadlineAt(sundayUtcMondayKst)).isEqualTo(kst("2026-10-13T00:00:00"));
    }

    @Test
    void 공휴일은_따로_제외하지_않는다() {
        // 2026-10-09(금, 한글날)도 영업일로 센다. 2026-10-08(목) 확정 → 금 월 화 수 목 → 다음 주 금요일 00:00
        assertThat(timeRules.shipDeadlineAt(kst("2026-10-08T10:00:00"))).isEqualTo(kst("2026-10-16T00:00:00"));
    }

    @Test
    void 기한_48시간은_실제_경과_시간이고_정확히_48시간부터_수동_요청을_거절한다() {
        Instant delivered = kst("2026-10-06T15:30:00");
        Instant deadline = timeRules.inspectionDeadlineAt(delivered);

        assertThat(Duration.between(delivered, deadline)).isEqualTo(Duration.ofHours(48));
        assertThat(TimeRules.isBeforeDeadline(deadline.minusNanos(1000), deadline)).isTrue();
        assertThat(TimeRules.isBeforeDeadline(deadline, deadline)).isFalse();
        assertThat(timeRules.refundResponseDeadlineAt(delivered)).isEqualTo(deadline);
    }

    @Test
    void 현재_시각은_DB_정밀도에_맞춰_마이크로초로_자른다() {
        Instant withNanos = Instant.parse("2026-10-05T01:00:00.123456789Z");
        TimeRules rules = new TimeRules(Clock.fixed(withNanos, ZoneOffset.UTC));
        assertThat(rules.now()).isEqualTo(Instant.parse("2026-10-05T01:00:00.123456Z"));
    }
}
