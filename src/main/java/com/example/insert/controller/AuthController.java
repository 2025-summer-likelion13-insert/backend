package com.example.insert.controller;

import com.example.insert.jwt.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(authentication.getPrincipal());
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refresh_token") String refreshToken) {
        try {
            String email = jwtTokenProvider.getSubject(refreshToken);
            String newAccessToken = jwtTokenProvider.generateAccessToken(email);
            return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
        } catch (io.jsonwebtoken.JwtException e) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid refresh token"));
        }
    }

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        // access_token 쿠키 삭제
        ResponseCookie accessTokenCookie = ResponseCookie.from("access_token", "")
                .path("/")
                .httpOnly(true)
                .maxAge(0)
                .build();

        // refresh_token 쿠키 삭제
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh_token", "")
                .path("/")
                .httpOnly(true)
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", accessTokenCookie.toString());
        response.addHeader("Set-Cookie", refreshTokenCookie.toString());

        return ResponseEntity.ok(Map.of("message", "로그아웃 성공"));
    }


}