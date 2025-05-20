package com.chatservice.redis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Duration;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RedisDto implements Serializable {
    
	private String key;         // Redis 저장 혹은 불러오는 키 값
    private String value;       // Redis 저장 값
    private Duration duration;      // TTL 설정 (초 단위)
    private String description; // 추가 설명 (옵션)
}
