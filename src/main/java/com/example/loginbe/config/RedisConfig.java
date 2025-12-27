package com.example.loginbe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableRedisRepositories
// Spring Data Redis의 레포지토리 기능을 활성화
// -> 인터페이스 기반으로 레디스 사용 가능
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    // Redis 연결 설정
    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        // Lettuce는 동기와 비동기 통신 모두를 지원하지만
        // Jedis는 동기식 통신만 지원
        // => 대량의 요청과 응답 처리에 있어서, Lettuce가 더욱 유리함
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(host);
        redisStandaloneConfiguration.setPort(port);

        return new LettuceConnectionFactory(redisStandaloneConfiguration);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        // Redis에 데이터를 읽고 쓰는 고수준 (메서드만 호출해도 스프링이 알아서 처리해줌) 의 추상화 제공
        // => 데이터 처리 방식 설정
        redisTemplate.setConnectionFactory(lettuceConnectionFactory());

        // 자바 객체를 Redis에 그대로 저장할 수 없음
        // -> StringRedisSerializer를 사용해 문자열 형태로 저장하도록 설정
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());

        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());

        return redisTemplate;
    }
}
