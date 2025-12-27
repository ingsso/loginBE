package com.example.loginbe.service;

import com.example.loginbe.dto.LoginRequestDto;
import com.example.loginbe.dto.LoginResponseDto;
import com.example.loginbe.dto.UserRequestDto;
import com.example.loginbe.entity.User;
import com.example.loginbe.repository.RedisDao;
import com.example.loginbe.repository.UserRepository;
import com.example.loginbe.security.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisDao redisDao;

    public LoginResponseDto signup(UserRequestDto req) {
        if (userRepository.findByPhone(req.getPhone()).isPresent()) {
            throw new RuntimeException("이미 사용 중인 전화번호 입니다.");
        }

        String verified = (String) redisDao.getValues("SMS_VERIFIED:" + req.getPhone());
        if (verified == null || !verified.equals("true")) {
            throw new RuntimeException("휴대폰 인증이 완료되지 않았습니다.");
        }

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .phone(req.getPhone())
                .role("ROLE_USER")
                .build();

        userRepository.save(user);

        redisDao.deleteValues("SMS_VERIFIED:" + req.getPhone());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), user.getRole());

        return new LoginResponseDto(accessToken, refreshToken);
    }

    public LoginResponseDto login(LoginRequestDto req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail(), user.getRole());

        return new LoginResponseDto(accessToken, refreshToken);
    }
}
