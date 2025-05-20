package com.chatservice.auth.filter.customfilter;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.chatservice.auth.exception.handler.LoginAuthenticationFailureHandler;
import com.chatservice.auth.filter.util.JWTUtil;
import com.chatservice.redis.handler.RedisHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.IOException;
import java.security.Key;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class LoginFilter extends UsernamePasswordAuthenticationFilter {

	private static final Logger logger = LoggerFactory.getLogger(LoginFilter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
	private final RedisHandler redisHandler;
    private final JWTUtil jwtUtil;
    
    
    public LoginFilter(AuthenticationManager authenticationManager, RedisHandler redisHandler, JWTUtil jwtUtil) {
        
    	super.setAuthenticationManager(authenticationManager);
    	this.setAuthenticationFailureHandler(new LoginAuthenticationFailureHandler());
    	
        this.redisHandler = redisHandler;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

        logger.info("LoginFilter: 로그인 요청 받음");
        // Content-Type 확인
        String contentType = request.getContentType();
        logger.info("Content-Type: {}", contentType);

        LoginRequest loginRequest = null;

        try {
            if ("application/json".equalsIgnoreCase(contentType)) {
            	
            	loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
            } else if ("application/x-www-form-urlencoded".equalsIgnoreCase(contentType)) {

                loginRequest = new LoginRequest(
                    request.getParameter("userid"),
                    request.getParameter("password")); 
            }
       } catch ( IOException e) { e.printStackTrace(); }
        
        // 인증 토큰 생성
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(loginRequest.getUserid(), loginRequest.getPassword());
        return getAuthenticationManager().authenticate(authToken);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain, Authentication authResult) {

    	logger.info("LoginFilter.successfulAuthentication - 사용자 : {}", authResult.getName());
        // JWT 생성 후 브러우저에 반환할 HostOnly 쿠키에 추가
        addJwtToCookie(response, generateAndStoreJwt(authResult));
        // 로그인 성공 후 페이지 이동
        try { response.sendRedirect("/ChatService/"); }
        catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                              AuthenticationException failed) throws IOException, ServletException {
        logger.warn("로그인 실패: {}", failed.getMessage());
        /* "LoginAuthenticationFailureHandler" 을 내부에서 자동 호출되어 처리. */
        super.unsuccessfulAuthentication(request, response, failed);
    }

    /**
     * JWT 생성 후 Redis에 저장
     * @return 생성된 JWT 토큰 문자열
     */
    private String generateAndStoreJwt(Authentication authResult) {
    	
        String username = authResult.getName();
        String userId = (String) authResult.getDetails();

        /* 브라우저 중복 로그인 요청 방지 */
        // 1. 동일한 userId 에 대한 JWT 가 포함되어 있는지 확인 위한 조회
        String existingToken = (String) redisHandler.getValueOperations().get(userId);
        // 2. 토큰이 포함되어 있는지 확인.
        if (existingToken != null) {
        	
        	System.out.println("existingToken : " + existingToken);
        	
        	redisHandler.getValueOperations().getOperations().delete(userId);			/* 기존 매핑 정보 삭제 [ 키 : userId - 값 : JWT ] */
            redisHandler.getValueOperations().getOperations().delete(existingToken);	/* 실제 인증 정보 삭제 [ 키 : JWT - 값 : key(문자열) ]. */
        }
        
        /* 새로운 JWT 생성 */
        // 사용자 역할(Authorities) 리스트
        List<String> roles = authResult.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // JWT 서명 키 생성하기.
        Key key = jwtUtil.generateSigningKey();
        // JWT 토큰 생성
        String jwtToken = jwtUtil.generateToken(username, userId, roles, key);
        /* 실제 인증 정보 저장 [ 키 : JWT - 값 : key(문자열) ]. */
        redisHandler.getValueOperations().set(jwtToken,jwtUtil.encodeKeyToBase64(key), 30, TimeUnit.MINUTES);
        /* 매핑 정보 저장 [ 키 : userId - 값 : JWT ] */
        redisHandler.getValueOperations().set(userId, jwtToken, 30, TimeUnit.MINUTES);
        return jwtToken;
    }
    /**
     * JWT 토큰을 응답 쿠키에 추가
     */
    private void addJwtToCookie(HttpServletResponse response, String jwtToken) {
        Cookie cookie = new Cookie("Authorization", jwtToken);
        cookie.setSecure(false);  // HTTPS에서만 사용
        cookie.setHttpOnly(true); // JavaScript 접근 방지
        cookie.setPath("/");      // 전체 경로에서 유효
        response.addCookie(cookie);
    }
    @Data
    @AllArgsConstructor
    static class LoginRequest {
    	String userid;
    	String password;
    }
}
