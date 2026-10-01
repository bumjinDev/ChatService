package com.chatservice.marketplace.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서비스가 현재 시각을 읽는 Clock 빈. 운영에서는 UTC 시스템 시계를 쓰고,
 * 테스트에서는 시각을 고정하거나 옮길 수 있는 Clock 으로 바꾼다(설계 명세서 4.3절).
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
