package com.example.loginbe;

import com.example.loginbe.security.util.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JwtTokenProviderTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("토큰 생성 및 복호화 테스트")
    void tokenTest() {
        // 1. 토큰 생성
        String email = "test@example.com";
        String role = "ROLE_USER";
        String token = jwtTokenProvider.generateAccessToken(email, role);

        // 확인: 토큰이 비어있지 않은지
        assertNotNull(token);
        System.out.println("Generated Token: " + token);

        // 2. 토큰에서 정보 추출
        String extractedEmail = jwtTokenProvider.getEmailFromToken(token);

        // 확인: 추출된 이메일이 원본과 같은지
        assertEquals(email, extractedEmail);

        // 3. 토큰 유효성 검증
        boolean isValid = jwtTokenProvider.validateToken(token);
        assertTrue(isValid);
    }
}