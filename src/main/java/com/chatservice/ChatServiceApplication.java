package com.chatservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
 * @SpringBootApplication 을 구성 어노테이션으로 풀어 쓴 이유:
 * 사용을 중단한 레거시 패키지(방 생성·입장·정원 permit·방 목록·방 집계·기존 WebSocket·범용 Redis API)를
 * 컴포넌트 스캔에서 제외해야 한다(설계 명세서 2.2.3, 8.2). @SpringBootApplication 에 별도의 @ComponentScan 을
 * 덧붙이면 두 스캔 설정이 함께 적용될 수 있으므로 스캔 설정을 하나로 둔다.
 * 제외한 패키지의 파일은 수정하지 않고 그대로 남긴다.
 */
@EnableScheduling
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = {
		@ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
		@ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
		@ComponentScan.Filter(type = FilterType.REGEX, pattern = {
				"com\\.chatservice\\.createroom\\..*",
				"com\\.chatservice\\.joinroom\\..*",
				"com\\.chatservice\\.concurrency\\..*",
				"com\\.chatservice\\.scheduler\\..*",
				"com\\.chatservice\\.roomlist\\..*",
				"com\\.chatservice\\.web\\..*",
				"com\\.chatservice\\.websocketcore\\..*",
				"com\\.chatservice\\.redis\\.controller\\..*",
				"com\\.chatservice\\.redis\\.service\\..*"
		})
})
public class ChatServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatServiceApplication.class, args);
	}
}
