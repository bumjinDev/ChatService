package com.chatservice.auth.exception.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

/**
 * JWT 기반 인가(Authorization) 실패를 처리하는 핸들러
 *
 * 실행 흐름:
 * 1. 사용자가 특정 리소스(`/admin/**`, `/manage/**` 등)에 접근 시도.
 * 2. `RequestAuthenticationFilter`에서 JWT 검증을 통과하여 `SecurityContextHolder`에 사용자 정보가 설정됨.
 * 3. Spring Security의 `FilterSecurityInterceptor`가 현재 사용자의 권한을 검사.
 *    - 사용자가 필요한 권한(`ROLE_ADMIN` 등)을 가지고 있지 않다면 `AccessDeniedException` 발생.
 * 4. `ExceptionTranslationFilter`가 예외를 감지하고 `AccessDeniedHandler` 실행.
 * 5. `JwtAccessDeniedHandler`가 실행되어 적절한 응답을 반환.
 *
 * 주요 역할:
 * - 인증된 사용자지만 접근 권한이 없는 경우 `403 Forbidden` 응답을 반환.
 * - 브라우저 요청이면 alert을 띄우고 `history.back();`을 실행하여 이전 페이지로 돌아가게 할 수 있다.
 * - API 요청이면 JSON 형식으로 `403 Forbidden` 응답을 반환할 수도 있다.
 *
 * ✅ 실무에서의 활용 예:
 * - 권한이 없는 리소스 접근 시도 로그를 저장하여 보안 감사 수행 가능.
 * - 특정 API 요청에서 `403 Forbidden`이 여러 번 발생하면 보안 경고 발생 가능.
 * - 관리자 페이지(`/admin/**`) 접근 시도 자체를 탐지하여 보안 로그에 기록할 수 있음.
 */
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger logger = LoggerFactory.getLogger(JwtAccessDeniedHandler.class);
    private final String AUTH_COOKIE_NAME = "Authorization";
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        logger.warn("인가 실패: {}", accessDeniedException.getMessage());

        response.setContentType("text/html; charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);	// 403 : 403 인가 에러

        // Authorization 쿠키 삭제
        Cookie expiredCookie = new Cookie(AUTH_COOKIE_NAME, null);
        expiredCookie.setPath("/");
        expiredCookie.setMaxAge(0);
        response.addCookie(expiredCookie);

        
        response.getWriter().write(
            "<script>" +
                "alert('접근 권한이 없습니다. 관리자에게 문의하세요.');" +
                "history.back();" +
            "</script>"
        );
    }
}

