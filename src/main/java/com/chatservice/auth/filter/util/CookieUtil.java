package com.chatservice.auth.filter.util;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;

/**
 * JWT를 쿠키에서 추출하는 유틸리티 클래스.
 *
 * <p>✅ **사용 위치:**</p>
 *
 * <h3>[시큐리티 컨텍스트]</h3>
 * <ul>
 *     <li>1. 로그인 필터 체인</li>
 *     <li>2. 게시판 필터 체인</li>
 *     <li>3. 로그인 성공 필터 체인</li>
 * </ul>
 *
 * <h3>[Spring MVC 컨텍스트]</h3>
 * <ul>
 *     <li>1. BoardService.writeReply()</li>
 * </ul>
 */
@Component
public class CookieUtil {

    /**
     * HTTP 요청의 쿠키 배열에서 특정 이름을 가진 JWT를 추출합니다.
     *
     * @param cookies    HTTP 요청의 쿠키 배열
     * @param cookieName JWT가 저장된 쿠키 이름
     * @return JWT 문자열 (쿠키가 없거나 해당 이름의 쿠키가 없으면 null)
     */
    public String extractJwtFromCookies(Cookie[] cookies, String cookieName) {
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue(); // JWT 토큰 반환
            }
        }

        return null; // 해당 쿠키가 존재하지 않을 경우 null 반환
    }
}
