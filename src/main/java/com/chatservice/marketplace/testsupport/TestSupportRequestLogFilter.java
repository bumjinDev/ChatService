package com.chatservice.marketplace.testsupport;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 테스트 지원 API 요청의 처리 시작과 처리 끝을 서버 로그에 남기는 필터.
 *
 * {@code /test-support/**} 요청은 어느 보안 필터 체인에도 속하지 않아 {@code JwtAuthProcessorFilter}의 「요청 처리 시작」 기록이 남지 않는다.
 * 그래서 서버 로그만으로는 미발송 자동 취소 같은 테스트 지원 요청의 로그와 SQL이 어느 요청의 것인지 구분할 수 없다.
 * 이 필터는 그 요청이 시작될 때와 끝날 때 한 줄씩 남겨, 같은 서버 스레드에서 두 기록 사이에 남은 로그를 한 요청의 것으로 볼 수 있게 한다.
 * 테스트 설정({@code marketplace.test-support.enabled})이 켜져 있을 때만 등록된다. 로그인한 회원의 요청에는 동작하지 않는다.
 */
// 서블릿 필터로 등록한다. 테스트 설정이 true일 때만 등록되며, 설정이 꺼져 있으면 이 필터는 없다.
@Component
@ConditionalOnProperty(name = "marketplace.test-support.enabled", havingValue = "true")
public class TestSupportRequestLogFilter extends OncePerRequestFilter {

    // 테스트 지원 요청의 처리 시작과 처리 끝을 서버 로그에 남기는 로거.
    private static final Logger logger = LoggerFactory.getLogger(TestSupportRequestLogFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 테스트 지원 API가 아닌 요청은 기록하지 않는다. 서블릿 경로에는 context-path(/ChatService)가 들어 있지 않다.
        return !request.getServletPath().startsWith("/test-support/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // JMeter가 요청 A·B에 붙이는 요청 이름. 처리 기준 시각 고정처럼 이름 헤더가 없는 요청은 null이다.
        String name = request.getHeader(TestPause.NAME_HEADER);
        logger.info("[테스트 지원 요청] 처리 시작 method={}, uri={}, query={}, name={}",
                request.getMethod(), request.getRequestURI(), request.getQueryString(), name);
        try {
            chain.doFilter(request, response);
        } finally {
            // 컨트롤러 처리가 끝나 응답 코드가 정해진 뒤에 남긴다. 처리 중 예외가 나도 남긴다.
            logger.info("[테스트 지원 요청] 처리 끝 method={}, uri={}, status={}, name={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), name);
        }
    }
}
