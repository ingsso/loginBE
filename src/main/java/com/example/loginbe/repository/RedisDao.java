package com.example.loginbe.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisDao {

    private final RedisTemplate<String,String> redisTemplate;
    private final ValueOperations<String,String> values;

    public RedisDao(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = (RedisTemplate<String, String>) (Object) redisTemplate;
        this.values = this.redisTemplate.opsForValue();
    }

    // 기본 데이터 저장
    public void setValues(String key, String data) {
        values.set(key, data);
    }

    // 만료 시간이 있는 데이터 저장 (주로 Refresh Token 저장)
    public void setValues(String key, String data, Duration duration) {
        values.set(key, data, duration);
    }

    // 데이터 조회 (Refresh Token 검증 시)
    public Object getValues(String key) {
        return values.get(key);
    }

    // 데이터 삭제 (Refresh Token 삭제 시)
    public void deleteValues(String key) {
        redisTemplate.delete(key);
    }
}
