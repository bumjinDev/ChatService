package com.chatservice.marketplace.order.scheduler;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chatservice.marketplace.order.IOrderCancellationService;

/**
 * 자동 처리를 주기적으로 실행한다(설계 명세서 7.1절). 실제 처리는 각 주문 서비스의 자동 처리 메서드에 위임한다.
 * 실행 간격은 marketplace.scheduler.fixed-delay(이전 실행이 끝난 뒤의 간격)이다.
 * marketplace.scheduler.enabled=false 이면 빈을 만들지 않는다(테스트에서 사용).
 */
@Component
@ConditionalOnProperty(name = "marketplace.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class TradeScheduler {

	private static final Logger log = LoggerFactory.getLogger(TradeScheduler.class);

	private final IOrderCancellationService cancellationService;
	private final Clock clock;

	public TradeScheduler(IOrderCancellationService cancellationService, Clock clock) {
		this.cancellationService = cancellationService;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${marketplace.scheduler.fixed-delay}")
	public void run() {
		Instant now = clock.instant();
		runJob("미발송 자동 취소", () -> cancellationService.cancelExpiredUnshipped(now));
	}

	/** 한 작업의 예외가 다른 작업의 실행을 막지 않도록 작업별로 예외를 기록한다. */
	private void runJob(String name, Runnable job) {
		try {
			job.run();
		} catch (RuntimeException e) {
			log.error("자동 처리 실패 작업={}", name, e);
		}
	}
}
