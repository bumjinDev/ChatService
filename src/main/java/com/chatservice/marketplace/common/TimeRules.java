package com.chatservice.marketplace.common;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

/**
 * 업무 시간 규칙(BR-006)을 계산한다. 업무 시간대는 KST(Asia/Seoul)이다.
 * - 발송 기한: 구매 확정 다음 영업일(월~금, 공휴일 미제외)을 1일째로 세어 5번째 영업일이 끝나는 시점.
 * - 48시간 기한: 기준 시각 + 48시간(실제 경과 시간).
 * 수동 요청은 현재 시각이 기한보다 작을 때 허용하고, 자동 처리는 현재 시각이 기한 이상일 때 대상으로 삼는다.
 */
@Component
public class TimeRules {

	public static final ZoneId KST = ZoneId.of("Asia/Seoul");
	public static final Duration FORTY_EIGHT_HOURS = Duration.ofHours(48);
	private static final int SHIPPING_BUSINESS_DAYS = 5;

	/** 발송 기한이 지나는 시점. 5번째 영업일 다음 날 00:00 KST 를 Instant 로 돌려준다. */
	public Instant shipDeadlineAt(Instant confirmedAt) {
		LocalDate day = confirmedAt.atZone(KST).toLocalDate();
		int count = 0;
		while (count < SHIPPING_BUSINESS_DAYS) {
			day = day.plusDays(1);
			if (isBusinessDay(day)) {
				count++;
			}
		}
		return day.plusDays(1).atStartOfDay(KST).toInstant();
	}

	/** 기준 시각에 48시간을 더한 기한. */
	public Instant fortyEightHoursAfter(Instant base) {
		return base.plus(FORTY_EIGHT_HOURS);
	}

	/** 수동 요청이 허용되는지: 현재 시각이 기한보다 작아야 한다. */
	public boolean isBefore(Instant now, Instant deadline) {
		return now.isBefore(deadline);
	}

	private boolean isBusinessDay(LocalDate day) {
		DayOfWeek dow = day.getDayOfWeek();
		return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
	}
}
