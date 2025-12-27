package com.example.loginbe.controller;

import com.example.loginbe.repository.RedisDao;
import lombok.RequiredArgsConstructor;
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

    /**
     * 1. 인증번호 발송 API
     */
    @PostMapping("/send-code")
    public ResponseEntity<String> sendVerificationCode(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");

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

        if (savedCode == null) {
            return ResponseEntity.badRequest().body("인증 시간이 만료되었거나 요청 이력이 없습니다.");
        }

        if (savedCode.equals(code)) {
            // 검증 성공 시 Redis 데이터 삭제 (재사용 방지)
            redisDao.deleteValues("SMS:" + phone);
            return ResponseEntity.ok("인증 성공");
        } else {
            return ResponseEntity.badRequest().body("인증번호가 일치하지 않습니다.");
        }
    }
}
