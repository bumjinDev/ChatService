package com.chatservice.user.exceptionhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * View 기반 컨트롤러 전용 예외 처리 핸들러
 *
 * - 대상: com.wherehouse.members.controller 패키지 내 @Controller 클래스
 * - 목적: 요청 처리 중 발생하는 주요 예외 상황에 대해 사용자에게 안내 메시지와 View 페이지를 반환
 * - 처리 방식: 예외 종류에 따라 Model에 에러 메시지를 설정한 뒤, JSP 또는 Thymeleaf 오류 페이지로 이동
 *
 * [주요 처리 예외 유형]
 * - 필수 쿠키/파라미터 누락
 * - 요청 파라미터 타입 불일치
 * - 잘못된 JSON 형식
 * - 도메인 내 잘못된 인자 전달 (IllegalArgumentException)
 * - 기타 예상되지 않은 런타임 예외 (Exception)
 */
@ControllerAdvice(basePackages = "com.wherehouse.members.controller")
public class MemberViewControllerExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(MemberViewControllerExceptionHandler.class);

    /**
     * 요청에 필수 쿠키가 누락
     *
     * - 예: @CookieValue(required = true) 사용 시, Authorization 쿠키가 없을 경우 발생
     * - 주로 로그인 상태 식별 실패와 관련
     */
    @ExceptionHandler(MissingRequestCookieException.class)
    public String handleMissingCookie(MissingRequestCookieException ex, Model model) {
        logger.warn("JWT 쿠키 누락: {}", ex.getMessage());
        model.addAttribute("message", "로그인 정보가 없습니다. 다시 로그인해주세요.");
        return "error/memberError";
    }

    /**
     * 요청에 필수 파라미터가 누락
     *
     * - 예: @RequestParam(required = true) 지정된 파라미터가 없을 경우 발생
     * - 예시: /members/edit?editid=123 에서 editid가 누락된 경우
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public String handleMissingParameter(MissingServletRequestParameterException ex, Model model) {
        logger.warn("필수 파라미터 누락: {}", ex.getParameterName());
        model.addAttribute("message", "잘못된 접근입니다. 필수 정보가 누락되었습니다.");
        return "error/memberError";
    }

    /**
     * 요청 파라미터 타입이 기대한 타입과 불일치
     *
     * - 예: @RequestParam("seq") int seq → /view?seq=abc 요청 시 발생
     * - 클라이언트에서 타입 불일치된 값을 보낸 경우
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String handleTypeMismatch(MethodArgumentTypeMismatchException ex, Model model) {
        logger.warn("파라미터 타입 불일치: {} → {}", ex.getName(), ex.getValue());
        model.addAttribute("message", "요청 파라미터의 형식이 올바르지 않습니다.");
        return "error/memberError";
    }

    /**
     * 잘못된 인자 전달로 발생하는 일반 예외 처리
     *
     * - 발생 위치: Service 계층, JWT 파싱 로직, 파라미터 검증 실패 등에서 직접 발생
     * - 예: JWT 유효성 검증 실패, null 입력값, 필수 Claim 누락 등
     * - 주의: 특정 도메인(JWT 등)에 한정된 예외가 아님
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException ex, Model model) {
        logger.error("잘못된 인자 처리 오류: {}", ex.getMessage());
        model.addAttribute("message", "요청 처리 중 오류가 발생했습니다. 입력값을 확인해주세요.");
        return "error/memberError";
    }

    /**
     * 그 외 정의되지 않은 예외에 대한 전역 처리
     *
     * - 예상하지 못한 런타임 오류 (NullPointerException, SQLException 등)
     * - 보안 로그 기록 및 사용자 안내 메시지 제공 목적
     */
    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, Model model) {
        logger.error("정의되지 않은 예외 발생", ex);
        model.addAttribute("message", "알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        return "error/genericError";
    }
}
