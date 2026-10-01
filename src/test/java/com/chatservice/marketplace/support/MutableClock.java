package com.chatservice.marketplace.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트에서 현재 시각을 지정하거나 앞으로 옮길 수 있는 Clock. */
public class MutableClock extends Clock {

	private volatile Instant now;

	public MutableClock(Instant initial) {
		this.now = initial;
	}

	public void set(Instant instant) {
		this.now = instant;
	}

	public void advance(Duration duration) {
		this.now = this.now.plus(duration);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return now;
	}
}
