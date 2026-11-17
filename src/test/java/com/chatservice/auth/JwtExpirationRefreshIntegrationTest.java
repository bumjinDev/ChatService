package com.chatservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.security.Key;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.mock.web.MockHttpServletResponse;

import com.chatservice.auth.filter.util.JWTUtil;
import com.chatservice.auth.filter.util.JwtExpirationRefresher;
import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.chatservice.redis.handler.RedisHandler;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;

/**
 * 인증된 요청이 로그인 만료 시각을 그 요청 시각 기준으로 갱신하는지 확인한다.
 * 인증이 필요한 지갑 조회(GET /api/wallet)를 요청으로 쓴다.
 */
class JwtExpirationRefreshIntegrationTest extends IntegrationTestSupport {

    private static final long LOGIN_TTL_SECONDS = JwtExpirationRefresher.LOGIN_TTL.toSeconds();

    @Autowired
    private RedisHandler redisHandler;
    @Autowired
    private JWTUtil jwtUtil;

    private final List<String> extraRedisKeys = new ArrayList<>();

    @AfterEach
    void deleteExtraRedisKeys() {
        for (String key : extraRedisKeys) {
            redis().delete(key);
        }
        extraRedisKeys.clear();
    }

    @Test
    void JWT_남은시간이_30분_이상이면_Redis_보존시간을_30분으로_다시_늘리고_JWT는_그대로_쓴다() throws Exception {
        TestMember member = member("refresh");
        // 로그인 뒤 29분이 지나 1분이 남은 상태를 만든다.
        redis().expire(member.token(), Duration.ofMinutes(1));
        redis().expire(member.id(), Duration.ofMinutes(1));

        MockHttpServletResponse response = getWallet(member.token());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getCookie("Authorization")).isNull();
        assertThat(ttlSeconds(member.token())).isGreaterThan(LOGIN_TTL_SECONDS - 10);
        assertThat(ttlSeconds(member.id())).isGreaterThan(LOGIN_TTL_SECONDS - 10);
    }

    @Test
    void JWT_남은시간이_30분보다_짧으면_새_JWT를_발급하고_예전_JWT는_30초_동안만_인증한다() throws Exception {
        TestMember member = member("reissue");
        Key key = jwtUtil.generateSigningKey();
        String keyValue = jwtUtil.encodeKeyToBase64(key);
        // 로그인 뒤 50분이 지나 JWT 만료까지 10분이 남은 상태를 만든다.
        String oldToken = tokenExpiringIn(member, key, Duration.ofMinutes(10));
        redisHandler.getValueOperations().set(oldToken, keyValue, 30, TimeUnit.MINUTES);
        redisHandler.getValueOperations().set(member.id(), oldToken, 30, TimeUnit.MINUTES);
        extraRedisKeys.add(oldToken);
        extraRedisKeys.add(JwtExpirationRefresher.REISSUE_MARK_PREFIX + oldToken);

        // 첫 요청: 새 JWT 를 발급해 쿠키로 돌려준다.
        MockHttpServletResponse first = getWallet(oldToken);

        assertThat(first.getStatus()).isEqualTo(200);
        Cookie newCookie = first.getCookie("Authorization");
        assertThat(newCookie).isNotNull();
        String newToken = newCookie.getValue();
        extraRedisKeys.add(newToken);
        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(newCookie.isHttpOnly()).isTrue();
        assertThat(newCookie.getPath()).isEqualTo("/");
        assertThat(jwtUtil.extractUserId(newToken, key)).isEqualTo(member.id());
        assertThat(jwtUtil.getRemainingDuration(newToken, key)).isGreaterThan(Duration.ofMinutes(59));
        assertThat(redisHandler.getValueOperations().get(newToken)).isEqualTo(keyValue);
        assertThat(ttlSeconds(newToken)).isGreaterThan(LOGIN_TTL_SECONDS - 10);
        assertThat(redisHandler.getValueOperations().get(member.id())).isEqualTo(newToken);
        assertThat(ttlSeconds(oldToken)).isBetween(1L, JwtExpirationRefresher.OLD_TOKEN_GRACE.toSeconds());

        // 같은 화면에서 함께 보낸 요청처럼 예전 JWT 로 다시 요청하면 인증은 되지만 JWT 를 또 발급하지 않는다.
        MockHttpServletResponse second = getWallet(oldToken);

        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(second.getCookie("Authorization")).isNull();
        assertThat(redisHandler.getValueOperations().get(member.id())).isEqualTo(newToken);
        assertThat(ttlSeconds(oldToken)).isBetween(1L, JwtExpirationRefresher.OLD_TOKEN_GRACE.toSeconds());

        // 새 JWT 로 요청하면 남은 시간이 30분 이상이므로 보존 시간만 늘린다.
        MockHttpServletResponse third = getWallet(newToken);

        assertThat(third.getStatus()).isEqualTo(200);
        assertThat(third.getCookie("Authorization")).isNull();
    }

    @Test
    void Redis_보존시간이_이미_지난_JWT는_갱신하지_않고_인증하지_않는다() throws Exception {
        TestMember member = member("expired");
        redis().delete(member.token());

        MockHttpServletResponse response = getWallet(member.token());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(redisHandler.getValueOperations().get(member.token())).isNull();
    }

    private MockHttpServletResponse getWallet(String token) throws Exception {
        return mockMvc.perform(get("/api/wallet").cookie(new Cookie("Authorization", token)))
                .andReturn().getResponse();
    }

    private String tokenExpiringIn(TestMember member, Key key, Duration remaining) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claim("username", member.nickname())
                .claim("userId", member.id())
                .claim("roles", List.of("ROLE_USER"))
                .setIssuedAt(new Date(now - Duration.ofMinutes(50).toMillis()))
                .setExpiration(new Date(now + remaining.toMillis()))
                .signWith(key)
                .compact();
    }

    private long ttlSeconds(String key) {
        Long seconds = redis().getExpire(key, TimeUnit.SECONDS);
        return seconds == null ? -2 : seconds;
    }

    private RedisOperations<String, Object> redis() {
        return redisHandler.getValueOperations().getOperations();
    }
}
