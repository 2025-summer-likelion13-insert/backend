package com.example.insert.config;

import com.example.insert.oauth.CustomOAuth2UserService;
import com.example.insert.oauth.OAuth2SuccessHandler;
import com.example.insert.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor

/*
public class SecurityConfig {

    private final CustomOAuth2UserService oAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login.html", "/oauth2/**", "/login/**", "/css/**", "/js/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .loginPage("/login.html") // 버튼 있는 그 페이지
                        .userInfoEndpoint(u -> u.userService(oAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> {
                            CookieUtil.clearCookie(res, "access_token");
                            CookieUtil.clearCookie(res, "refresh_token");
                            res.setStatus(HttpServletResponse.SC_OK);
                        })
                );

        return http.build();
    }
}
*/

public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // ✅ 임시로 모든 요청 허용
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                // ✅ 로그인 종류 전부 끄기 (리다이렉트 방지)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .oauth2Login(oauth2 -> oauth2.disable())    // ← 이거 중요!
                .logout(l -> l.disable());

        return http.build();
    }
}
