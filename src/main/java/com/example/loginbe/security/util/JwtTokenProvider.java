package com.example.loginbe.security.util;

import com.example.loginbe.repository.RedisDao;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final Key key;
    private final RedisDao redisDao;

    private static final long ACCESS_TOKEN_EXPIRE_TIME = 1000L * 60 * 30;
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 1000L * 60 * 60 * 24 * 7;

    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey,
                            RedisDao redisDao) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.redisDao = redisDao;
    }

    public String generateAccessToken(String email, String role){
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE_TIME))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String email){
        String refreshToken =  Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRE_TIME))
                .signWith(key)
                .compact();

        redisDao.setValues(email, refreshToken, Duration.ofMillis(REFRESH_TOKEN_EXPIRE_TIME));

        return refreshToken;
    }

    public String getEmailFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getSubject();
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        if (!validateToken(token)) return false;

        try {
            String email = getEmailFromToken(token);
            String redisToken = (String) redisDao.getValues(email);
            return token.equals(redisToken);
        } catch (Exception e) {
            return false;
        }
    }

    public void deleteRefreshToken(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        redisDao.deleteValues(username);
    }
}
