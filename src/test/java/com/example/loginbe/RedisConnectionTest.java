package com.example.loginbe;

import com.example.loginbe.repository.RedisDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class RedisConnectionTest {
    @Autowired
    private RedisDao redisDao;

    @Test
    void testRedis() {
        String key = "test_key";
        String value = "test_value";
        redisDao.setValues(key, value); // 데이터 저장
        assertEquals(value, redisDao.getValues(key)); // 데이터 조회 및 확인
    }
}