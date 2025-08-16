package com.example.insert.config;

import com.example.insert.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login.html", "/oauth2/**", "/login/**", "/css/**", "/js/**", 
                                       "/api/reviews/**", "/api/schedules/**", "/api/files/**", 
                                       "/api/place-recommendations/**", "/api/auth/**", "/h2-console/**").permitAll()
                        .anyRequest().authenticated()
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
