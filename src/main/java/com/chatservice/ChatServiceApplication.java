package com.chatservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
 * 컴포넌트 스캔 범위를 직접 지정한다.
 * - TypeExcludeFilter, AutoConfigurationExcludeFilter 는 @SpringBootApplication 의 기본 스캔 설정과 같다.
 * - REGEX 필터는 사용을 중단한 레거시 패키지를 빈 등록 대상에서 뺀다(설계 명세서 2.2.3절, 8.2절).
 *   소스 파일은 남겨 두고 실행에서만 제외한다.
 */
@EnableScheduling
@SpringBootApplication
@ComponentScan(
	basePackages = "com.chatservice",
	excludeFilters = {
		@ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
		@ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
		@ComponentScan.Filter(type = FilterType.REGEX, pattern = ChatServiceApplication.LEGACY_PACKAGE_PATTERN)
	})
public class ChatServiceApplication {

	/** 사용을 중단한 패키지: createroom, joinroom, concurrency, scheduler, roomlist, web, websocketcore, redis.controller, redis.service */
	static final String LEGACY_PACKAGE_PATTERN =
		"com\\.chatservice\\.(createroom|joinroom|concurrency|scheduler|roomlist|web|websocketcore|redis\\.controller|redis\\.service)\\..*";

	public static void main(String[] args) {
		SpringApplication.run(ChatServiceApplication.class, args);
	}
}
