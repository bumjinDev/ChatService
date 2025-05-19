package com.chatservice.auth.exception.handler;

import java.io.IOException;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.*;

/**
 * 로그인 인증 실패를 처리하는 핸들러
 *
 * Spring Security에서 로그인 요청(`/login` 경로)에 대한 인증이 실패했을 때 실행된다.
 * 
 * 실행 흐름:
 * 1. 사용자가 `/login` 경로로 POST 요청을 보냄.
 * 2. `LoginFilter`(`UsernamePasswordAuthenticationFilter`를 상속)가 요청을 처리하고
 *    `attemptAuthentication()`에서 `AuthenticationManager`를 통해 인증을 수행.
 * 3. `AuthenticationProvider`(`UserAuthenticationProvider`)에서 실제 인증을 시도.
 * 4. 인증이 실패하면 `AuthenticationException`이 발생 (`BadCredentialsException` 등).
 * 5. `UsernamePasswordAuthenticationFilter.unsuccessfulAuthentication()`가 호출됨.
 * 6. `setAuthenticationFailureHandler(new LoginAuthenticationFailureHandler());`을 설정해두었기 때문에
 *    `LoginAuthenticationFailureHandler`가 실행된다.
 *
 * 주요 역할:
 * - 로그인 실패 사유를 확인하고, 적절한 메시지를 클라이언트에게 전달한다.
 * - 기본적으로 `BadCredentialsException`이면 "아이디 또는 비밀번호가 잘못되었습니다." 메시지를 반환한다.
 * - 비활성화된 계정(`DisabledException`) 등 다른 인증 예외도 처리할 수 있다.
 * - 브라우저 요청이면 alert을 띄우고 `history.back();`으로 이전 페이지로 되돌아가게 할 수 있다.
 * - AJAX 요청이라면 JSON 형식으로 응답을 반환하도록 설정할 수도 있다.
 * 
 * 실무 활용 예:
 * - 로그인 실패 횟수를 기록하여 일정 횟수 초과 시 계정 잠금 처리 가능.
 * - 인증 실패 로그를 저장하여 보안 감사를 수행할 수 있다.
 * - 특정 시간 동안 여러 번 실패하면 보안 알람을 발생시킬 수 있다.
 */
public class LoginAuthenticationFailureHandler implements AuthenticationFailureHandler {

	private static final Logger logger = LoggerFactory.getLogger(LoginAuthenticationFailureHandler.class);
	

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException, ServletException {
		
		logger.error("Unauthorized request: {}", exception.getMessage());
		
		response.setContentType("text/html; charset=UTF-8");

        String errorMessage = "로그인에 실패했습니다. 다시 시도해주세요.";
        if (exception instanceof BadCredentialsException) {
            errorMessage = "아이디 또는 비밀번호가 잘못되었습니다.";
        } else if (exception instanceof DisabledException) {
            errorMessage = "계정이 비활성화되었습니다.";
        }
        
        response.getWriter().write("<script>alert('" + errorMessage + "'); window.location.href='/ChatService';</script>");

    }
}
