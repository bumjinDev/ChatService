package com.chatservice.marketplace.common.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서비스가 현재 시각을 읽는 유일한 출처. 테스트는 시각을 고정·이동할 수 있는 Clock 을 @Primary 로 주입한다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
