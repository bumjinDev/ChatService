package com.chatservice.auth.filter.util;

import java.security.Key;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import com.chatservice.redis.handler.RedisHandler;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT 인증에 성공한 요청마다 로그인 만료 시각을 그 요청 시각 기준으로 갱신한다.
 *
 * 로그인할 때(LoginFilter) 정하는 시간은 두 가지다.
 * - Redis 에 저장한 [JWT → 서명 키]와 [회원 ID → JWT]의 보존 시간 30분.
 *   [JWT → 서명 키]가 사라지면 JWT 가 맞아도 인증하지 않으므로, 로그인이 실제로 풀리는 시점은 이 시간이 정한다.
 * - JWT 안의 만료 시각. 발급 시각부터 1시간 뒤다.
 *
 * 이 클래스는 두 시간의 길이를 바꾸지 않고, 요청이 들어온 시각을 기준으로 만료 시각을 갱신한다.
 * - JWT 의 남은 시간이 30분 이상이면 두 Redis 값의 보존 시간을 다시 30분으로 늘린다.
 * - JWT 의 남은 시간이 30분보다 짧으면 보존 시간을 늘려도 JWT 가 먼저 만료된다.
 *   이때는 같은 클레임과 같은 서명 키로 만료 시각만 새로 정한 JWT 를 발급해 Redis 에 30분 동안 저장하고 쿠키를 바꾼다.
 *   같은 화면에서 함께 보낸 다른 요청은 예전 JWT 를 들고 도착하므로, 예전 JWT 의 Redis 값은 바로 지우지 않고 30초 뒤에 사라지게 한다.
 *   함께 도착한 요청이 저마다 새 JWT 를 발급하지 않도록, 예전 JWT 하나에 대해서는 먼저 도착한 요청 하나만 발급한다.
 */
@Component
public class JwtExpirationRefresher {

    private static final Logger logger = LoggerFactory.getLogger(JwtExpirationRefresher.class);
    private static final String AUTH_COOKIE_NAME = "Authorization";

    /** 로그인할 때 Redis 에 저장하는 보존 시간(LoginFilter 의 30분)과 같은 값이다. */
    public static final Duration LOGIN_TTL = Duration.ofMinutes(30);

    /** 새 JWT 를 발급한 뒤에도 예전 JWT 로 인증하는 시간. 이미 보낸 요청이 예전 쿠키를 들고 도착하는 동안만 허용한다. */
    public static final Duration OLD_TOKEN_GRACE = Duration.ofSeconds(30);

    /**
     * 예전 JWT 로 새 JWT 를 이미 발급했다는 표시의 Redis 키 앞부분과 보존 시간.
     * 표시가 예전 JWT 의 Redis 값(30초)보다 늦게 사라져야, 남은 30초 동안 도착한 요청이 예전 JWT 로 다시 발급하지 않는다.
     */
    public static final String REISSUE_MARK_PREFIX = "jwt-reissued:";
    public static final Duration REISSUE_MARK_TTL = Duration.ofSeconds(60);

    private final JWTUtil jwtUtil;
    private final RedisHandler redisHandler;

    public JwtExpirationRefresher(JWTUtil jwtUtil, RedisHandler redisHandler) {
        this.jwtUtil = jwtUtil;
        this.redisHandler = redisHandler;
    }

    /**
     * 인증 필터가 SecurityContext 에 인증 정보를 넣은 직후, 다음 필터로 넘기기 전에 호출한다.
     * 새 JWT 를 발급하면 응답에 쿠키를 넣어야 하므로 응답을 쓰기 전에 호출해야 한다.
     *
     * @param token    요청 쿠키에서 꺼낸 JWT
     * @param keyValue Redis 에서 꺼낸 서명 키 문자열
     * @param key      keyValue 로 만든 서명 키
     * @param userId   JWT 에 담긴 회원 ID
     */
    public void refresh(HttpServletRequest request, HttpServletResponse response,
                        String token, String keyValue, Key key, String userId) {
        try {
            ValueOperations<String, Object> values = redisHandler.getValueOperations();
            RedisOperations<String, Object> operations = values.getOperations();

            if (jwtUtil.getRemainingDuration(token, key).compareTo(LOGIN_TTL) >= 0) {
                operations.expire(token, LOGIN_TTL);
                operations.expire(userId, LOGIN_TTL);
                return;
            }

            Boolean firstRequest = values.setIfAbsent(REISSUE_MARK_PREFIX + token, userId, REISSUE_MARK_TTL);
            if (!Boolean.TRUE.equals(firstRequest)) {
                // 같은 JWT 를 들고 먼저 도착한 요청이 새 JWT 를 발급했다. 이 요청은 남은 30초 안에서 인증만 받는다.
                return;
            }

            String newToken = jwtUtil.renewExpiration(token, key);
            values.set(newToken, keyValue, LOGIN_TTL);
            values.set(userId, newToken, LOGIN_TTL);
            operations.expire(token, OLD_TOKEN_GRACE);
            addJwtToCookie(request, response, newToken);
            logger.info("JWT 재발급: 사용자 ID = {}", userId);
        } catch (RuntimeException e) {
            // 인증은 이미 성공했으므로 갱신에 실패해도 요청은 그대로 처리한다. 이때 로그인 만료 시각은 갱신 전 값으로 남는다.
            logger.warn("로그인 만료 시각 갱신 실패: 사용자 ID = {}", userId, e);
        }
    }

    /** 로그인할 때(LoginFilter.addJwtToCookie) 만드는 쿠키와 같은 속성으로 새 JWT 쿠키를 만든다. */
    private void addJwtToCookie(HttpServletRequest request, HttpServletResponse response, String jwtToken) {
        Cookie cookie = new Cookie(AUTH_COOKIE_NAME, jwtToken);
        cookie.setSecure(request.isSecure());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        response.addCookie(cookie);
    }
}
