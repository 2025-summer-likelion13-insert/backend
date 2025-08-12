package com.example.insert.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.HttpServletResponse;

public class CookieUtil {

    // 보안용(HttpOnly)
    public static void addHttpOnlyCookie(HttpServletResponse res, String name, String value, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(false)          // 로컬 http라면 false, https면 true
                .sameSite("Lax")        // Google OAuth 리다이렉트에도 안전
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // 개발 확인용(브라우저에서 읽힘)
    public static void addReadableCookie(HttpServletResponse res, String name, String value, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(false)        // JS에서 읽게 함
                .secure(false)          // 로컬 http
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public static void clearCookie(HttpServletResponse res, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}