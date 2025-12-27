package com.example.loginbe.security.filter;

import com.example.loginbe.service.CustomUserDetailsService;
import com.example.loginbe.security.util.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal (HttpServletRequest req, HttpServletResponse res,
                                     FilterChain filterChain)
        throws ServletException, IOException {

        String token = resolveToken(req);
        // HTTP 헤더에서 토큰을 추출함

        try {
            if (token != null &&  jwtTokenProvider.validateToken(token)) { // 토큰 존재 및 유효성 검사
                if (jwtTokenProvider.isBlacklisted(token)) {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                String identifier = jwtTokenProvider.getEmailFromToken(token);

                UserDetails userDetails = userDetailsService.loadUserByUsername(identifier);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
                // 검증이 끝난 사용자 정보를 SecurityContext에 담아둠
                // => 컨트롤러에서 사용자 정보를 원할 때 Spring에서 바로 응답 가능
            }
        } catch (ExpiredJwtException e) {
            req.setAttribute("exception", "EXPIRED_TOKEN");
        } catch (JwtException e) {
            req.setAttribute("exception", "INVALID_TOKEN");
        }

        filterChain.doFilter(req, res);
    }

    // 순수 토큰 문자열 추출
    private String resolveToken (HttpServletRequest req) {
        String bearer = req.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
