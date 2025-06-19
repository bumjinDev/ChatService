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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class JwtAuthProcessorFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthProcessorFilter.class);
    private static final String AUTH_COOKIE_NAME = "Authorization";

    private final CookieUtil cookieUtil;
    private final JWTUtil jwtUtil;
    private final RedisHandler redisHandler;

    public JwtAuthProcessorFilter(
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
        logger.info("JWTAuthenticationFilter - 요청 처리 시작");
        logger.info("HTTP Method: {}, URL: {}", request.getMethod(), request.getRequestURL());
        // 1. 쿠키에서 JWT 추출
        Optional<String> tokenOpt = Optional.ofNullable(
            cookieUtil.extractJwtFromCookies(request.getCookies(), AUTH_COOKIE_NAME));
        
        if (tokenOpt.isEmpty()) {
            logger.warn("HTTPRequest Message 내 JWT 토큰이 존재하지 않음");
            filterChain.doFilter(request, response);
            return;
        }
        String token = tokenOpt.get();
        String keyValue = (String) redisHandler.getValueOperations().get(token);
        /* 2. JWT 토큰에 대한 Redius 내 Key 데이터의 TTL 만료 여부 확인.  */
        if(keyValue == null) {
        	logger.warn("HTTPRequest Message 내 JWT 토큰에 대한 Key 값 없음!");
        	SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }
        // 3. JWT에 대한 key 값을 가져온 후 이를 서명 키 생성 해서 가져오기
        Optional<Key> keyOpt = jwtUtil.getSigningKeyFromToken(keyValue);
        if (keyOpt.isEmpty()) {
            logger.warn("JWT 서명 키 없음");
            filterChain.doFilter(request, response);
            return;
        }
        Key key = keyOpt.get();
        // 4. JWT 검증
        if (!jwtUtil.isValidToken(token, key)) {
            logger.warn("JWT 토큰 검증 실패");
            filterChain.doFilter(request, response);
            return;
        }
        // 4. 사용자 ID 및 권한 추출
        String userId = jwtUtil.extractUserId(token, key);
        String userName = jwtUtil.extractUsername(token, key);
        
        request.setAttribute("userName", userName);		/* WebSocket 프레임 워크 단에서는 mvc Controller 와 다르게 Authentication 주입 불가능..추후 원인 파악 필요..원인 파악 전까지 임시 대처 */
        
        var authorities = jwtUtil.extractRoles(token, key).stream()
                .map(SimpleGrantedAuthority::new).toList();
        // 5. SecurityContext에 Authentication 설정
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);

        authentication.setDetails(userName);		    /* 실시간 채팅 방 페이지에서 필요함..*/
        request.setAttribute("userId", userId);   /* WebSocket.handler() 에서 'SemaphoreRegistry' 추적을 위해 WebSocket.ChatHandShakeIntercepter() 에서 사용 */

        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        logger.info("JWT 인증 성공: 사용자 ID = {}", userId);
        logger.info("JWT 인증 성공: 사용자 이름 = {}", userName);

        // 필터 체인 계속 실행
        filterChain.doFilter(request, response);
    }
}