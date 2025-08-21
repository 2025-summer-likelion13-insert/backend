package com.example.insert.util;

import com.example.insert.entity.User;
import com.example.insert.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    public Long idOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new RuntimeException("UNAUTHORIZED");

        Object p = auth.getPrincipal();

        // 네 프로젝트의 CustomOAuth2User 지원
        if (p instanceof com.example.insert.oauth.CustomOAuth2User cu) {
            String email = cu.getEmail();
            return userRepository.findByEmail(email)
                    .map(User::getId)
                    .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND_BY_EMAIL: " + email));
        }

        // 혹시 기본 OAuth2User로 들어오는 경우도 방어
        if (p instanceof OAuth2User ou) {
            String email = ou.getAttribute("email");
            return userRepository.findByEmail(email)
                    .map(User::getId)
                    .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND_BY_EMAIL: " + email));
        }

        throw new RuntimeException("CANNOT_RESOLVE_USER_ID");
    }
}
