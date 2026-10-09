package com.chatservice.marketplace.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class IntegrationTestConfig {

    /** 운영용 Clock.systemUTC() 대신 주입한다. 각 테스트가 시작 시각을 다시 설정한다. */
    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(Instant.parse("2026-10-05T01:00:00Z"));
    }
}
