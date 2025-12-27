package com.example.loginbe.controller;

import com.example.loginbe.dto.LoginRequestDto;
import com.example.loginbe.dto.LoginResponseDto;
import com.example.loginbe.dto.UserRequestDto;
import com.example.loginbe.entity.User;
import com.example.loginbe.repository.RedisDao;
import com.example.loginbe.repository.UserRepository;
import com.example.loginbe.service.KakaoOAuthService;
import com.example.loginbe.service.UserService;
import com.example.loginbe.security.util.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final KakaoOAuthService kakaoOAuthService;
    private final UserRepository userRepository;
    private final RedisDao redisDao;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody UserRequestDto req,
                                                   HttpServletResponse res) {
        try {
            LoginResponseDto tokens = userService.signup(req);
            return getRefreshCookie(res, tokens);
        } catch (RuntimeException e) {
            // 중복 번호 등 비즈니스 로직 예외 발생 시 400 Bad Request와 메시지 반환
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto req,
                                                  HttpServletResponse res) {
        LoginResponseDto tokens = userService.login(req);

        return getRefreshCookie(res, tokens);
    }

    @PostMapping("/kakao")
    public ResponseEntity<?> kakaoLogin(@RequestBody Map<String, String> body,
                                        HttpServletResponse res) {
        String code = body.get("code");
        return ResponseEntity.ok(kakaoOAuthService.kakaoLogin(code, res));
    }

    @PostMapping("/link-social")
    @Transactional
    public ResponseEntity<?> linkSocial(@RequestBody Map<String, String> body, HttpServletResponse response) { // response 추가
        String phone = body.get("phone");
        String socialId = body.get("socialId");

        // 1. Redis 인증 확인
        String verified = (String) redisDao.getValues("SMS_VERIFIED:" + phone);
        if (verified == null) return ResponseEntity.badRequest().body("인증이 필요합니다.");

        try {
            Optional<User> existingUserOpt = userRepository.findByPhone(phone);

            if (existingUserOpt.isPresent()) {
                User existingUser = existingUserOpt.get();

                // 2-1. 임시 소셜 계정 삭제 (중복 충돌 방지)
                userRepository.findBySocialIdAndProvider(socialId, "kakao")
                        .ifPresent(tmp -> {
                            userRepository.delete(tmp);
                            userRepository.flush(); // 즉시 DB 반영하여 Unique 제약 조건 충돌 방지
                        });

                // 2-2. 기존 계정에 소셜 정보 업데이트
                existingUser.setSocialId(socialId);
                existingUser.setProvider("kakao");
                userRepository.save(existingUser);

                return generateLoginResponse(existingUser, response); // response 전달
            } else {
                User socialUser = userRepository.findBySocialIdAndProvider(socialId, "kakao")
                        .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));
                socialUser.setPhone(phone);
                userRepository.save(socialUser);

                return generateLoginResponse(socialUser, response); // response 전달
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("연결 실패: " + e.getMessage());
        }
    }

    private ResponseEntity<?> generateLoginResponse(User user, HttpServletResponse response) {
        // 1. 토큰 생성
        // socialId를 기반으로 토큰을 생성하도록 설계된 기존 로직을 따릅니다.
        String accessToken = jwtTokenProvider.generateAccessToken(user.getSocialId(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getSocialId(), user.getRole());

        // 2. 응답 DTO 생성
        LoginResponseDto tokens = new LoginResponseDto(accessToken, refreshToken);

        // 3. 리프레시 토큰을 쿠키에 저장 (기존에 정의하신 getRefreshCookie 활용)
        return getRefreshCookie(response, tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest req) {
        String refreshToken = null;
        if (req.getCookies() != null) {
            for (Cookie cookie : req.getCookies()) {
                if (cookie.getName().equals("refreshToken")) {
                    refreshToken = cookie.getValue();
                }
            }
        }

        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않는 refreshToken 입니다.");
        }

        if (jwtTokenProvider.isBlacklisted(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그아웃된 토큰입니다.");
        }

        String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        String role = jwtTokenProvider.getRoleFromToken(refreshToken);

        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("서버의 토큰 정보와 일치하지 않습니다.");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(email, role);

        return ResponseEntity.ok(new LoginResponseDto(newAccessToken, null));
    }

    @PostMapping("/logout")
    public String logout(@RequestHeader("Authorization") String accessToken,
                         HttpServletResponse res) {
        String token = accessToken.replace("Bearer ", "");
        String email = jwtTokenProvider.getEmailFromToken(token);

        jwtTokenProvider.deleteRefreshToken(email);
        jwtTokenProvider.addToBlacklist(token);

        Cookie refreshCookie = new Cookie("refreshToken", null);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setMaxAge(0);
        refreshCookie.setPath("/");
        res.addCookie(refreshCookie);

        return "로그아웃 성공";
    }

    private ResponseEntity<LoginResponseDto> getRefreshCookie(HttpServletResponse res,
                                                              LoginResponseDto tokens) {
        Cookie refreshCookie = new Cookie("refreshToken", tokens.getRefreshToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(7 * 24 * 60 * 60);
        res.addCookie(refreshCookie);

        return ResponseEntity.ok(new LoginResponseDto(tokens.getAccessToken(), null));
    }
}
