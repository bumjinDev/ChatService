package com.chatservice.marketplace.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 테스트 기준 시각. */
public final class TestTimes {

	public static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 2026-09-28(월) 10:00 KST */
	public static final Instant BASE = kst(2026, 9, 28, 10, 0);

	private TestTimes() {
	}

	public static Instant kst(int year, int month, int day, int hour, int minute) {
		return LocalDateTime.of(year, month, day, hour, minute).atZone(KST).toInstant();
	}
}
