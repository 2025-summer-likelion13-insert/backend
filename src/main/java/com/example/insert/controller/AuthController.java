package com.example.insert.controller;

import com.example.insert.jwt.JwtTokenProvider;
import com.example.insert.entity.User;
import com.example.insert.repository.UserRepository;
import com.example.insert.util.CookieUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    // ✅ 로그인 여부 확인 (id + email 반환)
    @GetMapping("/me")
    public ResponseEntity<?> me(@CookieValue(name = "access_token", required = false) String accessToken) {
        if (accessToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "no token"));
        }

        try {
            String email = jwtTokenProvider.getSubject(accessToken);

            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "user not found"));
            }

            return ResponseEntity.ok(Map.of(
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "name", user.getName()
            ));
        } catch (JwtException e) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid token"));
        }
    }

    // ✅ refresh_token으로 새 access_token 발급
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken,
                                     HttpServletResponse response) {
        if (refreshToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "no refresh token"));
        }

        try {
            String email = jwtTokenProvider.getSubject(refreshToken);
            String newAccessToken = jwtTokenProvider.generateAccessToken(email);

            CookieUtil.addHttpOnlyCookie(response, "access_token", newAccessToken, 3600);
            CookieUtil.addReadableCookie(response, "access_token_dev", newAccessToken, 3600);

            return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
        } catch (JwtException e) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid refresh token"));
        }
    }

    // ✅ 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        CookieUtil.clearCookie(response, "access_token");
        CookieUtil.clearCookie(response, "refresh_token");
        CookieUtil.clearCookie(response, "access_token_dev");

        return ResponseEntity.ok(Map.of("message", "로그아웃 성공"));
    }
}
