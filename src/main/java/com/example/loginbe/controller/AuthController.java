package com.example.loginbe.controller;

import com.example.loginbe.repository.RedisDao;
import com.example.loginbe.repository.UserRepository;
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

    /**
     * 1. 인증번호 발송 API
     */
    @PostMapping("/send-code")
    public ResponseEntity<String> sendVerificationCode(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        boolean isSocial = body.get("isSocial") != null;

        // 소셜 통합이 아닐 때만(즉, 일반 생 신규 가입일 때만) 중복 체크
        if (!isSocial && userRepository.findByPhone(phone).isPresent()) {
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
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String code = body.get("code");

        String savedCode = (String) redisDao.getValues("SMS:" + phone);

        if (savedCode != null && savedCode.equals(code)) {
            // 1. 기존 인증번호는 삭제
            redisDao.deleteValues("SMS:" + phone);

            // 2. [중요!] 계정 통합 API가 확인할 수 있도록 "인증 성공 플래그"를 Redis에 저장
            // 이 한 줄이 없으면 link-social API에서 무조건 400 에러가 납니다.
            redisDao.setValues("SMS_VERIFIED:" + phone, "true", Duration.ofMinutes(5));

            return ResponseEntity.ok("인증 성공");
        } else {
            return ResponseEntity.badRequest().body("인증번호가 일치하지 않습니다.");
        }
    }
}
