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

        for (int i = 0; i < 5; i++) {
            long startTime = System.currentTimeMillis();
            redisDao.setValues(key, value);
            long endTime = System.currentTimeMillis();
            System.out.println("순수 Redis 작업 소요 시간: " + (endTime - startTime) + "ms");
        }
    }
}