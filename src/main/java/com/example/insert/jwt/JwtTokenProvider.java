// src/main/java/com/example/insert/jwt/JwtTokenProvider.java
package com.example.insert.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKeyStr;

    @Value("${jwt.access-token-validity}")
    private long accessTokenValidity;

    @Value("${jwt.refresh-token-validity}")
    private long refreshTokenValidity;

    private Key key;

    @PostConstruct
    void init() {
        // 반드시 32바이트 이상
        this.key = Keys.hmacShaKeyFor(secretKeyStr.getBytes());
    }

    public String generateAccessToken(String email) {
        return createToken(email, accessTokenValidity);
    }

    public String generateRefreshToken(String email) {
        return createToken(email, refreshTokenValidity);
    }

    private String createToken(String subject, long validityMillis) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + validityMillis);

        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** ✅ 토큰에서 subject(email) 꺼내는 공용 메서드 */
    public String getSubject(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
}
