package com.chatservice.redis.config;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis 환경 설정
 *
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    // application.yml의 spring.data.redis.password 주입. 미설정(로컬 무인증) 시 빈 문자열로 치환.
    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // 기존 new LettuceConnectionFactory(host, port) 는 host/port만 적용하고
        // password를 누락한다. 인증이 걸린 미니PC Redis에는 NOAUTH로 연결이 거부되므로,
        // RedisStandaloneConfiguration으로 password까지 명시 적용한다.
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        if (password != null && !password.isBlank()) {
            config.setPassword(RedisPassword.of(password));
        }
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {

        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();

        redisTemplate.setConnectionFactory(redisConnectionFactory());

        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());

        redisTemplate.setDefaultSerializer(new StringRedisSerializer());

        return redisTemplate;
    }

    /* 테이블 "MapData" 내 모든 테이터 한꺼번에 가져오는 것. */
    @Bean
    public RedisTemplate<String, Map<String, List<Map<String, Double>>>> redisTemplateAllMapData(RedisConnectionFactory redisConnectionFactory) {

        RedisTemplate<String, Map<String, List<Map<String, Double>>>> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // JSON 직렬화 설정 (타입 명시적으로 지정)
        ObjectMapper objectMapper = new ObjectMapper();
        Jackson2JsonRedisSerializer<Map<String, List<Map<String, Double>>>> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper.getTypeFactory().constructType(new TypeReference<Map<String, List<Map<String, Double>>>>() {}));

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        return template;
    }
}