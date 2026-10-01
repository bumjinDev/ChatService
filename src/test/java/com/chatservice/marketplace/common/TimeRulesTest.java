package com.chatservice.marketplace.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.chatservice.marketplace.support.TestTimes;

/** BR-006 영업일 계산 검증(설계 명세서 9장 마지막 행). */
class TimeRulesTest {

	private final TimeRules timeRules = new TimeRules();

	@Test
	void 월요일_확정은_다음주_화요일_0시에_기한이_지난다() {
		Instant monday = TestTimes.kst(2026, 9, 28, 10, 0);
		assertThat(timeRules.shipDeadlineAt(monday)).isEqualTo(TestTimes.kst(2026, 10, 6, 0, 0));
	}

	@Test
	void 금요일_확정은_다음주_토요일_0시에_기한이_지난다() {
		Instant friday = TestTimes.kst(2026, 10, 2, 23, 59);
		assertThat(timeRules.shipDeadlineAt(friday)).isEqualTo(TestTimes.kst(2026, 10, 10, 0, 0));
	}

	@Test
	void 토요일_확정은_다음주_토요일_0시에_기한이_지난다() {
		Instant saturday = TestTimes.kst(2026, 10, 3, 9, 0);
		assertThat(timeRules.shipDeadlineAt(saturday)).isEqualTo(TestTimes.kst(2026, 10, 10, 0, 0));
	}

	@Test
	void KST_날짜_기준으로_계산한다() {
		// UTC 로는 일요일 16:00 이지만 KST 로는 월요일 01:00 이다.
		Instant mondayKst = Instant.parse("2026-09-27T16:00:00Z");
		assertThat(timeRules.shipDeadlineAt(mondayKst)).isEqualTo(TestTimes.kst(2026, 10, 6, 0, 0));
	}

	@Test
	void 사십팔시간_기한은_기준_시각에_48시간을_더한다() {
		Instant base = Instant.parse("2026-09-28T01:00:00Z");
		assertThat(timeRules.fortyEightHoursAfter(base)).isEqualTo(Instant.parse("2026-09-30T01:00:00Z"));
	}
}
