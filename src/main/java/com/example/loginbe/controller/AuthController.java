package com.example.loginbe.controller;

import com.example.loginbe.dto.LoginResponseDto;
import com.example.loginbe.entity.User;
import com.example.loginbe.repository.RedisDao;
import com.example.loginbe.repository.UserRepository;
import com.example.loginbe.security.util.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RedisDao redisDao;
    private final UserRepository userRepository;
    private final JwtTokenProvider  jwtTokenProvider;

    /**
     * 1. 인증번호 발송 API
     */
    @PostMapping("/send-code")
    public ResponseEntity<String> sendVerificationCode(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");

        if (userRepository.findByPhone(phone).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("이미 가입된 휴대폰 번호입니다.");
        }

        // 6자리 난수 생성 (100000 ~ 999999)
        String verificationCode = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));

        // Redis에 휴대폰 번호를 키로 하여 인증번호 저장 (유효시간 3분 설정)
        // RedisDao의 setValues(String key, String data, Duration duration) 활용
        redisDao.setValues("SMS:" + phone, verificationCode, Duration.ofMinutes(3));

        // 콘솔 출력 (실제 운영 시에는 외부 SMS API를 호출하여 문자를 발송해야 합니다)
        System.out.println("휴대폰: " + phone + " / 인증번호: " + verificationCode);

        return ResponseEntity.ok("인증번호가 발송되었습니다.");
    }

    /**
     * 2. 인증번호 검증 로직 (회원가입 시 함께 처리 권장)
     * 별도의 API가 필요하다면 아래와 같이 작성합니다.
     */
    @PostMapping("/verify-code")
    public ResponseEntity<String> verifyCode(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String code = body.get("code");

        String savedCode = (String) redisDao.getValues("SMS:" + phone);

        if (savedCode != null && savedCode.equals(code)) {
            // 인증 성공 시, '인증 완료 플래그'를 Redis에 5분간 저장
            // 이 플래그가 있어야 실제 회원가입 API가 진행됨
            redisDao.setValues("SMS_VERIFIED:" + phone, "true", Duration.ofMinutes(5));

            // 기존의 일회성 인증번호는 삭제
            redisDao.deleteValues("SMS:" + phone);
            return ResponseEntity.ok("인증 성공");
        } else {
            return ResponseEntity.badRequest().body("인증번호가 일치하지 않거나 만료되었습니다.");
        }
    }

    @PostMapping("/link-social")
    public ResponseEntity<?> linkSocial(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String socialId = body.get("socialId");

        // 1. Redis 인증 여부 확인
        String verified = (String) redisDao.getValues("SMS_VERIFIED:" + phone);
        if (verified == null || !verified.equals("true")) {
            return ResponseEntity.badRequest().body("휴대폰 인증이 만료되었거나 유효하지 않습니다.");
        }

        try {
            // 2. 계정 통합 로직 실행
            User user = userRepository.findByPhone(phone)
                    .map(existingUser -> {
                        existingUser.setSocialId(socialId);
                        existingUser.setProvider("kakao");
                        return userRepository.save(existingUser);
                    })
                    .orElseGet(() -> {
                        User socialUser = userRepository.findBySocialIdAndProvider(socialId, "kakao")
                                .orElseThrow(() -> new RuntimeException("소셜 가입 정보를 찾을 수 없습니다."));
                        socialUser.setPhone(phone);
                        return userRepository.save(socialUser);
                    });

            redisDao.deleteValues("SMS_VERIFIED:" + phone); // 인증 정보 사용 후 삭제
            String accessToken = jwtTokenProvider.generateAccessToken(user.getSocialId(), user.getRole());
            return ResponseEntity.ok(new LoginResponseDto(accessToken, null));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
