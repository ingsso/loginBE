package com.example.loginbe.service;

import com.example.loginbe.entity.User;
import com.example.loginbe.repository.UserRepository;
import com.example.loginbe.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        // 1. 먼저 이메일로 검색
        User user = userRepository.findByEmail(identifier)
                // 2. 이메일로 없으면 socialId로 검색 (카카오 등 소셜 로그인 대응)
                .orElseGet(() -> userRepository.findBySocialId(identifier)
                        .orElseThrow(() -> new UsernameNotFoundException("해당 식별자로 사용자를 찾을 수 없습니다: " + identifier)));

        return new CustomUserDetails(user);
    }
}
