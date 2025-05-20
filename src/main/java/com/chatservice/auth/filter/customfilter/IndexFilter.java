package com.chatservice.auth.filter.customfilter;

import java.io.IOException;
import java.security.Key;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.chatservice.auth.filter.util.CookieUtil;
import com.chatservice.auth.filter.util.JWTUtil;
import com.chatservice.redis.handler.RedisHandler;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie; // Cookie import
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class IndexFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthProcessorFilter.class);
    private static final String AUTH_COOKIE_NAME = "Authorization";

    private final CookieUtil cookieUtil;
    private final JWTUtil jwtUtil;
    private final RedisHandler redisHandler;

    public IndexFilter(
    		CookieUtil cookieUtil,
    		JWTUtil jwtUtil,
    		RedisHandler redisHandler)
    {
        this.cookieUtil = cookieUtil;
        this.jwtUtil = jwtUtil;
        this.redisHandler = redisHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
                                    throws ServletException, IOException {
        logger.info("JwtAuthProcessorFilter - 요청 처리 시작");
        logger.info("HTTP Method: {}, URL: {}", request.getMethod(), request.getRequestURL());

        // 1. 쿠키에서 JWT 추출
        Optional<String> tokenOpt = Optional.ofNullable(
            cookieUtil.extractJwtFromCookies(request.getCookies(), AUTH_COOKIE_NAME));

        boolean authenticationFailed = false; // 인증 실패 여부를 추적하는 플래그

        if (tokenOpt.isEmpty()) {
            logger.warn("HTTPRequest Message 내 JWT 토큰이 존재하지 않음");
            authenticationFailed = true; // 토큰 없음 == 인증 실패
        } else {
            String token = tokenOpt.get();
            String keyValue = (String) redisHandler.getValueOperations().get(token);

            /* 2. JWT 토큰에 대한 Redius 내 Key 데이터의 TTL 만료 여부 확인.  */
            if(keyValue == null) {
            	logger.warn("HTTPRequest Message 내 JWT 토큰에 대한 Key 값 없음 (Redis 만료 또는 없음)");
                authenticationFailed = true; // Redis 키 없음 == 인증 실패
            } else {
                // 3. JWT에 대한 key 값을 가져온 후 이를 서명 키 생성 해서 가져오기
                Optional<Key> keyOpt = jwtUtil.getSigningKeyFromToken(keyValue);
                if (keyOpt.isEmpty()) {
                    logger.warn("JWT 서명 키 없음");
                    authenticationFailed = true; // 서명 키 생성 실패 == 인증 실패
                } else {
                    Key key = keyOpt.get();
                    // 4. JWT 검증
                    if (!jwtUtil.isValidToken(token, key)) {
                        logger.warn("JWT 토큰 검증 실패");
                        authenticationFailed = true; // 토큰 유효성 검증 실패 == 인증 실패
                    } else {
                        // --- 인증 성공 ---
                        String userId = jwtUtil.extractUserId(token, key);
                        String userName = jwtUtil.extractUsername(token, key);

                        // WebSocket 핸들링 등에서 필요할 수 있는 사용자 이름 속성 설정
                        request.setAttribute("userName", userName);

                        var authorities = jwtUtil.extractRoles(token, key).stream()
                                .map(SimpleGrantedAuthority::new).toList();

                        // Security Context에 인증 정보 설정
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userId, null, authorities);
                        authentication.setDetails(userName); // 부가 정보 설정
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        logger.info("JWT 인증 성공: 사용자 ID = {}", userId);
                        logger.info("JWT 인증 성공: 사용자 이름 = {}", userName);
                    }
                }
            }
        }

        // --- 인증 실패 시 쿠키 초기화 로직 추가 ---
        if (authenticationFailed) {
             logger.info("인증 실패 플래그 감지. Authorization 쿠키 삭제 처리.");
             // 인증 실패 시 SecurityContextHolder를 확실하게 비웁니다.
             SecurityContextHolder.clearContext();

             // Authorization 쿠키를 즉시 만료시키기 위해 MaxAge를 0으로 설정
             Cookie expiredCookie = new Cookie(AUTH_COOKIE_NAME, null);
             expiredCookie.setPath("/"); // 쿠키 경로 설정 (원래 쿠키 설정과 동일하게)
             expiredCookie.setMaxAge(0); // 즉시 만료
             // 원래 Authorization 쿠키의 설정과 동일하게 httpOnly, Secure 등을 설정해주는 것이 좋습니다.
             // 예: expiredCookie.setHttpOnly(true);
             // 예: if (request.isSecure()) expiredCookie.setSecure(true);
             response.addCookie(expiredCookie);

             // permitAll() 경로이므로 여기서 응답을 커밋하거나 401 상태를 보내지 않습니다.
             // 요청은 다음 필터 체인으로 계속 진행됩니다.
        }

        // 인증 성공 또는 실패와 상관없이 다음 필터 체인을 계속 실행
        filterChain.doFilter(request, response);
    }
}