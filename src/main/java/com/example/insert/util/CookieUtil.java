package com.example.insert.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import jakarta.servlet.http.HttpServletResponse;

public class CookieUtil {

    // TODO: 운영/개발 자동 분기는 spring.profiles.active 읽어서 바꿀 수 있음
    private static final boolean IS_PROD = true; // 운영: true, 로컬 테스트: false

    // 보안용(HttpOnly)
    public static void addHttpOnlyCookie(HttpServletResponse res, String name, String value, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(IS_PROD)                          // 운영: true (HTTPS), 로컬: false
                .sameSite(IS_PROD ? "None" : "Lax")       // 운영: cross-site 허용
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // 개발 확인용(브라우저에서 읽힘)
    public static void addReadableCookie(HttpServletResponse res, String name, String value, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(false)                          // JS에서 읽게 함
                .secure(IS_PROD)
                .sameSite(IS_PROD ? "None" : "Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public static void clearCookie(HttpServletResponse res, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(IS_PROD)
                .sameSite(IS_PROD ? "None" : "Lax")
                .path("/")
                .maxAge(0)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
