package com.example.insert.oauth;

import com.example.insert.jwt.JwtTokenProvider;
import com.example.insert.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// OAuth2SuccessHandler.java
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User user = (CustomOAuth2User) authentication.getPrincipal();

        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        // 실제 사용 쿠키(서버에서만 읽음)
        CookieUtil.addHttpOnlyCookie(response, "access_token", accessToken, 3600);
        CookieUtil.addHttpOnlyCookie(response, "refresh_token", refreshToken, 1209600);

        // 개발 확인용 쿠키(브라우저 JS에서 읽힘) - 배포 전 제거 권장
        CookieUtil.addReadableCookie(response, "access_token_dev", accessToken, 3600);

        // 로그인 후 다시 로그인 페이지로 돌아오게
        getRedirectStrategy().sendRedirect(request, response, "/login.html");
    }
}

