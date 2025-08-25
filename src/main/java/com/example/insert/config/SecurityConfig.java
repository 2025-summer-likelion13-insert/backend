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


                // 모든 요청 허용
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )

                // OAuth2 로그인 활성화 (구글 로그인 가능하게)
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login.html")  // 커스텀 로그인 페이지 (없으면 스프링 기본 로그인 페이지 사용)
                        .defaultSuccessUrl("/", true) // 로그인 성공 후 메인으로 이동
                )

                // 로그아웃 처리
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> {
                            res.setStatus(HttpServletResponse.SC_OK);
                        })

                );

        // ★ JWT 인증 필터 추가 (쿠키/Authorization 헤더에서 토큰 읽어 SecurityContext 세팅)
        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userRepository),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
