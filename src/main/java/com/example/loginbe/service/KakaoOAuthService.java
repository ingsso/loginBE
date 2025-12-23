package com.example.loginbe.service;

import com.example.loginbe.dto.LoginResponseDto;
import com.example.loginbe.entity.User;
import com.example.loginbe.repository.UserRepository;
import com.example.loginbe.security.util.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    public LoginResponseDto kakaoLogin(String code, HttpServletResponse response) {
        // 1. 인가코드로 액세스 토큰 요청
        String kakaoAccessToken = getKakaoAccessToken(code);

        // 2. 액세스 토큰으로 사용자 정보 요청
        Map<String, Object> userInfo = getKakaoUserInfo(kakaoAccessToken);

        // 3. 응답 데이터 파싱
        String socialId = String.valueOf(userInfo.get("id")); // 고유 ID
        Map<String, Object> kakaoAccount = (Map<String, Object>) userInfo.get("kakao_account");
        String email = (String) kakaoAccount.get("email");

        log.info("카카오 로그인 시도 - ID: {}, Email: {}", socialId, email);

        // 4. 사용자 저장 또는 업데이트 (Provider: kakao)
        User user = userRepository.findBySocialIdAndProvider(socialId, "kakao")
                .orElseGet(() -> userRepository.save(new User(socialId, "kakao", email, "ROLE_USER")));

        // 5. JWT 토큰 발행
        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        // 6. Refresh Token DB 업데이트 및 쿠키 설정
        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        setRefreshTokenCookie(response, refreshToken);

        return new LoginResponseDto(accessToken, null);
    }

    private String getKakaoAccessToken(String code) {
        String tokenUrl = "https://kauth.kakao.com/oauth/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        return (String) response.getBody().get("access_token");
    }

    private Map<String, Object> getKakaoUserInfo(String accessToken) {
        String userInfoUrl = "https://kapi.kakao.com/v2/user/me";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<?> request = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(userInfoUrl, HttpMethod.GET, request, Map.class);

        return (Map<String, Object>) response.getBody();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setMaxAge(7 * 24 * 60 * 60); // 7일
        refreshCookie.setPath("/");
        response.addCookie(refreshCookie);
    }
}