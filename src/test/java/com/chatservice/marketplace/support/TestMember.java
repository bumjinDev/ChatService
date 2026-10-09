package com.chatservice.marketplace.support;

import jakarta.servlet.http.Cookie;

/** 통합 테스트용 회원. token 은 Redis 에 서명키가 저장된 실제 JWT 이며 기존 JwtAuthProcessorFilter 로 인증된다. */
public record TestMember(String id, String nickname, String token) {

    public Cookie cookie() {
        return new Cookie("Authorization", token);
    }

    public String cookieHeader() {
        return "Authorization=" + token;
    }
}
