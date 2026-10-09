package com.chatservice.marketplace.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * H2 보조 실행 전용 데이터소스(-PintegrationDb=h2).
 *
 * 저장소의 DDL 파일(ddl_toychat.sql, ddl_marketplace.sql)을 그대로 읽어 실행해서,
 * 테스트 전용 스키마를 따로 손으로 만들지 않는다. H2 는 Oracle 이 아니므로 이 실행 결과를
 * Oracle DDL·매핑 검증으로 보고하지 않는다.
 */
@TestConfiguration
@Profile("integration-h2")
public class H2IntegrationDataSourceConfig {

    static final Path DDL_DIR = Path.of("docs", "1. 프로젝트개발", "2. db");

    @Bean
    public DataSource dataSource(@Value("${spring.datasource.url}") String url,
                                 @Value("${spring.datasource.username}") String username,
                                 @Value("${spring.datasource.password}") String password) throws SQLException, IOException {
        // 기본 application.yml 의 hikari.data-source-properties(oracle.net.*)는 H2 가 거부하므로 쓰지 않는다.
        DataSource dataSource = DataSourceBuilder.create()
                .driverClassName("org.h2.Driver")
                .url(url)
                .username(username)
                .password(password)
                .build();
        try (Connection connection = dataSource.getConnection()) {
            // 같은 JVM 에서 컨텍스트가 다시 만들어지면 메모리 DB 가 남아 있으므로 한 번만 만든다.
            if (!tableExists(connection, "PRODUCT")) {
                runScript(connection, DDL_DIR.resolve("ddl_toychat.sql"));
                runScript(connection, DDL_DIR.resolve("ddl_marketplace.sql"));
            }
        }
        return dataSource;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var tables = connection.getMetaData().getTables(null, null, table, null)) {
            return tables.next();
        }
    }

    private static void runScript(Connection connection, Path script) throws IOException {
        String sql = Files.readString(script, StandardCharsets.UTF_8);
        ScriptUtils.executeSqlScript(connection,
                new EncodedResource(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }
}
