package com.chatservice.auth.filter.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.security.Key;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
/**
 * JWT 토큰의 생성, 검증, 서명 키 관리 및 클레임(Claims) 조작 기능을 담당하는 유틸 클래스.
 */
@Component
public class JWTUtil {

    private static final long EXPIRATION_TIME = 3600000;

    /**
     * HMAC-SHA256 서명 키를 생성
     */
    public Key generateSigningKey() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    /**
     * Key 객체를 Base64 문자열로 변환
     */
    public String encodeKeyToBase64(Key key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getEncoded());
    }

    /**
     * JWT 에 해당하는 key 문자열(redis 추출)을 받아 서명 키 객체로 반환.
     */
    public Optional<Key> getSigningKeyFromToken(String keyString) {
         
        return Optional.of(
        		new SecretKeySpec(
            Base64.getUrlDecoder().decode(keyString), SignatureAlgorithm.HS256.getJcaName()
        ));
    }
    
    /**
     * JWT 토큰을 생성
     */
    public String generateToken(String username, String userId, List<String> roles, Key key) {
        Date now = new Date();
        return Jwts.builder()
                .claim("username", username)
                .claim("userId", userId)
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + EXPIRATION_TIME))
                .signWith(key)
                .compact();
    }
    /**
     * JWT 검증
     */
    public boolean isValidToken(String token, Key key) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
   
    /**
     * JWT 토큰에서 모든 Claims 추출
     */
    public Claims extractAllClaims(String token, Key key) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            throw new AccessDeniedException("유효하지 않은 JWT입니다.");
        }
    }
    /**
     * JWT 토큰에서 특정 클레임 수정 후 새로운 토큰 생성
     */
    public String modifyClaim(String token, Key key, String claimName, Object newValue) {
        Claims claims = extractAllClaims(token, key);
        claims.put(claimName, newValue);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime() + EXPIRATION_TIME))
                .signWith(key)
                .compact();
    }
    
    /**
     * JWT 토큰에서 사용자 ID 추출
     */
    public String extractUserId(String token, Key key) {
        return extractAllClaims(token, key).get("userId", String.class);
    }

    /**
     * JWT 토큰에서 사용자 이름 추출
     */
    public String extractUsername(String token, Key key) {
        return extractAllClaims(token, key).get("username", String.class);
    }

    /**
     * JWT 토큰에서 역할 리스트 추출
     */
    public List<String> extractRoles(String token, Key key) {
        Claims claims = extractAllClaims(token, key);
        Object rolesObj = claims.get("roles");

        if (rolesObj instanceof List<?>) {
            return ((List<?>) rolesObj).stream()
                    .map(Object::toString) // 안전하게 String 변환
                    .collect(Collectors.toList());
        }
        return List.of(); // 빈 리스트 반환
    }


    
    
    /**
     * JWT 토큰의 발급 시간 추출
     */
    public Date extractIssuedAt(String token, Key key) {
        return extractAllClaims(token, key).getIssuedAt();
    }

    /**
     * JWT 토큰의 만료 시간 추출
     */
    public Date extractExpiration(String token, Key key) {
        return extractAllClaims(token, key).getExpiration();
    }

    /**
     * JWT 토큰이 만료되었는지 확인
     */
    public Boolean isTokenExpired(String token, Key key) {
        return extractExpiration(token, key).before(new Date());
    }
    
    /**
     * JWT 토큰의 남은 유효 기간을 반환
     * @param token JWT 토큰
     * @param key 서명 키
     * @return 남은 유효 시간 (Duration)
     */
    public Duration getRemainingDuration(String token, Key key) {
        Date expiration = extractExpiration(token, key);
        long remainingMillis = expiration.getTime() - System.currentTimeMillis();
        return remainingMillis > 0 ? Duration.ofMillis(remainingMillis) : Duration.ZERO;
    }
}
