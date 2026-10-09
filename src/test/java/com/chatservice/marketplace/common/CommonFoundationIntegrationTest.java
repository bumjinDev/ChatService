package com.chatservice.marketplace.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.fasterxml.jackson.databind.JsonNode;

/** 공통 기반: 보안 체인, 오류 응답 형식, 레거시 스캔 제외(설계 2.2, 5.1, 8.2). */
class CommonFoundationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void 인증_없이_API를_호출하면_401_JSON을_받는다() throws Exception {
        JsonNode body = json(getJson("/api/wallet", null).andExpect(status().isUnauthorized()));
        assertThat(body.get("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(body.get("status").asInt()).isEqualTo(401);
    }

    @Test
    void 회원가입_입력_검증_실패는_새_오류_형식으로_응답한다() throws Exception {
        JsonNode body = json(postJson("/members/join", null, Map.of("id", "ab"))
                .andExpect(status().isBadRequest()));
        assertThat(body.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("fieldErrors").has("id")).isTrue();
        assertThat(body.get("fieldErrors").has("pw")).isTrue();
    }

    @Test
    void 사용_중단한_방_기능은_빈으로_만들어지지_않고_경로는_404이다() throws Exception {
        assertThat(applicationContext.containsBean("roomJoinViewController")).isFalse();
        assertThat(applicationContext.containsBean("webController")).isFalse();
        assertThat(applicationContext.containsBean("chatSessionRegistry")).isFalse();
        assertThat(applicationContext.containsBean("redisSingleDataController")).isFalse();

        TestMember member = member("legacy");
        mockMvc.perform(get("/rooms").cookie(member.cookie())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/redis/singleData/getValue").cookie(member.cookie())).andExpect(status().isNotFound());
    }

    @Test
    void 메인_화면은_방_집계_없이_index를_렌더링한다() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"));
    }
}
