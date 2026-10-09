package com.chatservice.marketplace.order.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import com.chatservice.marketplace.support.IntegrationTestSupport;

/** 운영 설정처럼 스케줄러를 켰을 때 빈이 만들어지고 의존성이 연결되는지만 확인한다. 실행 결과는 각 서비스 테스트가 확인한다. */
@TestPropertySource(properties = {
        "marketplace.scheduler.enabled=true",
        "marketplace.scheduler.fixed-delay=3600000"
})
class TradeSchedulerWiringIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void 스케줄러를_켜면_TradeScheduler_빈이_만들어진다() {
        assertThat(applicationContext.getBeansOfType(TradeScheduler.class)).hasSize(1);
    }
}
