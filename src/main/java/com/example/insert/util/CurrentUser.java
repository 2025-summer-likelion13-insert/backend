package com.example.insert.util;

import com.example.insert.entity.User;
import com.example.insert.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    /**
     * SecurityContext에서 현재 요청 사용자의 userId를 얻는다.
     * - JwtAuthenticationFilter가 details에 userId(Long)를 넣어둔 경우: 바로 사용
     * - principal이 CustomOAuth2User: getEmail()로 조회
     * - principal이 OAuth2User: attribute("email")로 조회
     * - principal이 UserDetails: getUsername()을 이메일로 보고 조회
     * - principal이 String: 이메일로 보고 조회
     * 실패 시 401(UNAUTHORIZED)로 던진다.
     */
    public Long idOrThrow() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw unauthorized("인증 정보가 없습니다(SecurityContext 비어있음).");
        }

        // 1) 필터에서 details에 userId를 박아둔 경우(가장 빠르고 명확)
        Object details = authentication.getDetails();
        if (details instanceof Long uid) {
            return uid;
        }

        // 2) principal 유형별 처리
        Object principal = authentication.getPrincipal();

        // 2-1) 커스텀 OAuth2 사용자(이 프로젝트 타입)
        if (principal instanceof com.example.insert.oauth.CustomOAuth2User cu) {
            String email = cu.getEmail();
            return findIdByEmailOrThrow(email);
        }

        // 2-2) 표준 OAuth2User
        if (principal instanceof OAuth2User ou) {
            String email = ou.getAttribute("email");
            return findIdByEmailOrThrow(email);
        }

        // 2-3) 일반 UserDetails (스프링 시큐리티 기본 사용자)
        if (principal instanceof UserDetails ud) {
            String username = ud.getUsername(); // 이메일로 사용하는 경우가 일반적
            return findIdByEmailOrThrow(username);
        }

        // 2-4) 단순 문자열 principal (JWT 필터에서 email을 principal로 넣은 경우)
        if (principal instanceof String emailStr) {
            return findIdByEmailOrThrow(emailStr);
        }

        // 어떤 경우에도 매칭 안 되면 인증 실패로 본다
        throw unauthorized("현재 사용자 ID를 해석할 수 없습니다(principal=" + principal + ").");
    }

    private Long findIdByEmailOrThrow(String email) {
        if (email == null || email.isBlank()) {
            throw unauthorized("이메일 정보가 비어있습니다.");
        }
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> unauthorized("해당 이메일의 사용자를 찾을 수 없습니다: " + email));
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
