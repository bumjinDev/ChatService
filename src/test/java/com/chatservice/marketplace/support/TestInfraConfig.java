package com.chatservice.marketplace.support;

import javax.sql.DataSource;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * 통합 테스트용 빈.
 * - MutableClock 을 기본(@Primary) Clock 으로 쓴다.
 * - 테스트 스키마에 테이블이 없으면 DDL 스크립트(ddl_toychat.sql, ddl_marketplace.sql)를 적용한다.
 *   스크립트는 build.gradle 의 processTestResources 가 docs 에서 클래스패스 db/ 로 복사한다.
 */
@TestConfiguration
public class TestInfraConfig {

	@Bean
	@Primary
	public MutableClock mutableClock() {
		return new MutableClock(TestTimes.BASE);
	}

	@Bean
	public SchemaInitializer schemaInitializer(DataSource dataSource) {
		return new SchemaInitializer(dataSource);
	}

	/** 빈 생성 시점에 테이블 존재 여부를 확인하고 없는 스크립트만 실행한다. */
	public static class SchemaInitializer {

		public SchemaInitializer(DataSource dataSource) {
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			if (!tableExists(jdbc, "MEMBERTBL")) {
				run(dataSource, "ddl_toychat.sql");
			}
			if (!tableExists(jdbc, "PRODUCT")) {
				run(dataSource, "ddl_marketplace.sql");
			}
		}

		private static boolean tableExists(JdbcTemplate jdbc, String table) {
			Integer count = jdbc.queryForObject(
					"SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME = ?", Integer.class, table);
			return count != null && count > 0;
		}

		private static void run(DataSource dataSource, String fileName) {
			ClassPathResource script = new ClassPathResource("db/" + fileName);
			if (!script.exists()) {
				throw new IllegalStateException("DDL 스크립트가 클래스패스에 없습니다: db/" + fileName);
			}
			ResourceDatabasePopulator populator = new ResourceDatabasePopulator(script);
			populator.setSqlScriptEncoding("UTF-8");
			populator.execute(dataSource);
		}
	}
}
