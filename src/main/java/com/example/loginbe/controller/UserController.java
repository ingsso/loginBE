package com.example.loginbe.controller;

import com.example.loginbe.dto.LoginRequestDto;
import com.example.loginbe.dto.LoginResponseDto;
import com.example.loginbe.dto.UserRequestDto;
import com.example.loginbe.service.KakaoOAuthService;
import com.example.loginbe.service.UserService;
import com.example.loginbe.security.util.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final KakaoOAuthService kakaoOAuthService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<LoginResponseDto> signup(@RequestBody UserRequestDto req,
                                                   HttpServletResponse res) {
        LoginResponseDto tokens = userService.signup(req);

        return getRefreshCookie(res, tokens);
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
        LoginResponseDto tokens = kakaoOAuthService.kakaoLogin(code, res);
        return getRefreshCookie(res, tokens);
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
