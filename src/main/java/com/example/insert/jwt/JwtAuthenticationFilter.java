package com.example.insert.jwt;

import com.example.insert.entity.User;
import com.example.insert.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    private Optional<String> getCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            // 1) 쿠키에서 토큰 시도
            String token = getCookie(request, "access_token")
                    .or(() -> getCookie(request, "access_token_dev"))
                    .orElse(null);

            // 2) Authorization 헤더(Bearer)도 허용
            if (token == null) {
                String auth = request.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    token = auth.substring(7);
                }
            }

            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String email = jwtTokenProvider.getSubject(token);

                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            email, null, Collections.emptyList()); // 필요 시 ROLE 추가
                    // details에 userId를 담아두면 CurrentUser에서 꺼내 쓰기 좋음
                    auth.setDetails(user.getId());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (Exception ignored) {
            // 토큰 문제는 인증 없이 진행. 보호된 경로면 401로 떨어짐
        }
        chain.doFilter(request, response);
    }
}
