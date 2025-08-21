package com.example.insert.security;

import com.example.insert.entity.User;
import com.example.insert.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class AuthUtils {
    private final UserRepository userRepository;

    public Long getUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) throw new IllegalStateException("인증 정보 없음");
        Object p = auth.getPrincipal();

        if (p instanceof Jwt jwt) {
            Object claim = jwt.getClaim("userId"); // 프로젝트에 userId 클레임이 있으면 여기서 끝
            if (claim instanceof Number) return ((Number) claim).longValue();
            String email = jwt.getClaim("email");
            if (email != null) return userRepository.findByEmail(email).orElseThrow().getId();
        }
        if (p instanceof OAuth2User o) {
            String email = o.getAttribute("email");
            if (email != null) return userRepository.findByEmail(email).orElseThrow().getId();
        }
        if (p instanceof UserDetails ud) {
            return userRepository.findByEmail(ud.getUsername()).orElseThrow().getId();
        }
        if (p instanceof String s) {
            return userRepository.findByEmail(s).orElseThrow().getId();
        }
        throw new IllegalStateException("지원하지 않는 Principal: " + p.getClass().getName());
    }
}
