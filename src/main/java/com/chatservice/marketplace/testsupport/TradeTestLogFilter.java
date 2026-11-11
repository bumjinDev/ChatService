package com.chatservice.marketplace.testsupport;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 거래 정합성 테스트 요청의 수신과 응답을 공통 형식으로 남기고, 테스트명·실행 ID·요청 ID 를 요청 처리 스레드에 묶는 필터.
 *
 * 실행 ID 헤더와 요청 ID 헤더가 모두 있는 요청만 다룬다. 요청을 받으면 세 값을 스레드에 묶고 단계 {@code 요청수신}을 남긴다.
 * 그 뒤의 인증, 컨트롤러, 서비스, 트랜잭션 콜백은 같은 스레드에서 실행되므로 {@link TradeTestLog}가 같은 값을 쓴다.
 * 응답 코드가 정해지면 단계 {@code 요청응답}에 응답 상태, 업무 오류 코드, 소요 시간을 남기고 스레드에 묶은 값을 지운다.
 *
 * 로그인 확인(보안 필터 체인)보다 먼저 실행되도록 순서를 앞에 둔다. 그래서 소요 시간에는 로그인 확인 시간이 들어가고,
 * 로그인 실패(401) 응답도 기록된다. 테스트 설정({@code marketplace.test-support.enabled})이 켜져 있을 때만 등록된다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(name = "marketplace.test-support.enabled", havingValue = "true")
public class TradeTestLogFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TradeTestLogFilter.class);

    private static final String FEATURE = "요청 수신과 응답";
    private static final String FILE = "TradeTestLogFilter.java";
    private static final String METHOD = "doFilterInternal";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(TradeTestLog.RUN_ID_HEADER) == null
                || request.getHeader(TradeTestLog.REQUEST_ID_HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startedNanos = System.nanoTime();
        TradeTestLog.begin(request.getHeader(TradeTestLog.TEST_NAME_HEADER),
                request.getHeader(TradeTestLog.RUN_ID_HEADER), request.getHeader(TradeTestLog.REQUEST_ID_HEADER));
        TradeTestLog.event(logger, FEATURE, FILE, METHOD, "요청수신",
                "HTTP메소드", request.getMethod(), "경로", request.getRequestURI(),
                "스레드", Thread.currentThread().getName());
        Throwable propagated = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            propagated = e;
            throw e;
        } finally {
            long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000;
            if (propagated == null) {
                TradeTestLog.event(logger, FEATURE, FILE, METHOD, "요청응답",
                        "응답상태", response.getStatus(), "오류코드", TradeTestLog.errorCode(), "소요시간ms", elapsedMillis);
            } else {
                // 예외가 필터 밖으로 나가면 응답 코드는 이 뒤에 서블릿 컨테이너가 정한다. 여기서 읽은 응답 코드는 최종 값이 아니므로 적지 않는다.
                TradeTestLog.event(logger, FEATURE, FILE, METHOD, "요청응답",
                        "응답상태", "예외전파", "오류코드", TradeTestLog.errorCode(), "소요시간ms", elapsedMillis,
                        "예외", rootCause(propagated).getClass().getSimpleName());
            }
            TradeTestLog.end();
        }
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
