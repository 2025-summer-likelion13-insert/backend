package com.example.insert.config;

import com.example.insert.jwt.JwtAuthenticationFilter;
import com.example.insert.jwt.JwtTokenProvider;
import com.example.insert.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenProvider jwtTokenProvider,
                                                   UserRepository userRepository) throws Exception {
        http
                // CSRF 비활성화 (테스트 용)
                .csrf(csrf -> csrf.disable())

                // 요청 인가: 찜 관련만 인증 요구, 나머지는 그대로 허용
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/likes/**").authenticated()
                        .anyRequest().permitAll()
                )

                // OAuth2 로그인(구글)
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login.html")              // 커스텀 로그인 페이지
                        .defaultSuccessUrl("/", true)          // 로그인 성공 후 메인으로
                )

                // 로그아웃
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
                );

        // ★ JWT 인증 필터 추가 (쿠키/Authorization 헤더에서 토큰 읽어 SecurityContext 세팅)
        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userRepository),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
