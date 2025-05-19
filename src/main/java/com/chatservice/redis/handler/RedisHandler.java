package com.chatservice.redis.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

@Component
//@RequiredArgsConstructor
public class RedisHandler {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * ListOperations : Redis 리스트 자료구조 연산용
     */
    public ListOperations<String, Object> getListOperations() {
        return redisTemplate.opsForList();
    }

    /**
     * ValueOperations : 단일 Key-Value 연산용
     */
    public ValueOperations<String, Object> getValueOperations() {
        return redisTemplate.opsForValue();
    }

    /**
     * HashOperations : 해시 구조 접근 연산용
     */
    public HashOperations<String, String, Object> getHashOperations() {
        return redisTemplate.opsForHash();
    }

    /**
     * 일반적인 Redis 작업에 대한 예외 처리 래퍼
     */
    public int executeOperation(Runnable operation) {
        try {
            operation.run();
            return 1;
        } catch (Exception e) {
            System.out.println("Redis 작업 오류 발생 :: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 현재 선택된 Redis DB 초기화
     */
    public void clearCurrentRedisDB() {
        redisConnectionFactory.getConnection().serverCommands().flushDb();
        System.out.println("✅ Redis 현재 DB 초기화 완료");
    }
}
