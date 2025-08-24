package com.example.insert.oauth;

import com.example.insert.jwt.JwtTokenProvider;
import com.example.insert.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        Object principal = authentication.getPrincipal();

        String email;
        if (principal instanceof CustomOAuth2User customUser) {
            email = customUser.getEmail();
        } else if (principal instanceof OidcUser oidcUser) {
            email = oidcUser.getAttribute("email");
        } else {
            throw new IllegalStateException("Unknown principal type: " + principal.getClass());
        }

        String accessToken = jwtTokenProvider.generateAccessToken(email);
        String refreshToken = jwtTokenProvider.generateRefreshToken(email);

        CookieUtil.addHttpOnlyCookie(response, "access_token", accessToken, 3600);
        CookieUtil.addHttpOnlyCookie(response, "refresh_token", refreshToken, 1209600);
        CookieUtil.addReadableCookie(response, "access_token_dev", accessToken, 3600);

        getRedirectStrategy().sendRedirect(request, response, "https://insertsajahoo.netlify.app");
    }
}
