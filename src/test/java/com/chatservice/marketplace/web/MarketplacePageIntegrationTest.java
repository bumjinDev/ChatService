package com.chatservice.marketplace.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;

/**
 * 실제 포트로 메인 화면·대화 화면 JSP 와 화면 스크립트·스타일이 제공되는지 확인한다.
 * 화면 동작은 이 테스트가 아니라 브라우저로 따로 확인했다(판단 기록 J-27).
 */
class MarketplacePageIntegrationTest extends IntegrationTestSupport {

    /* 브라우저는 type="module" 스크립트의 MIME 이 JavaScript 가 아니면 실행하지 않는다. */
    private static final List<String> MODULES = List.of(
            "/js/market/api.js", "/js/market/dom.js", "/js/market/format.js", "/js/market/shell.js",
            "/js/market/app.js", "/js/market/conversation-page.js",
            "/js/market/views/products.js", "/js/market/views/sell.js", "/js/market/views/wallet.js",
            "/js/market/views/orders.js");

    private HttpResponse<String> get(String path, String cookieHeader) throws Exception {
        HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ChatService" + path)).GET();
        if (cookieHeader != null) {
            request.header("Cookie", cookieHeader);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return get(path, null);
    }

    @Test
    void 대화_화면이_화면_모듈을_싣는다() throws Exception {
        HttpResponse<String> page = get("/conversations?conversationId=1");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body())
                .contains("<script type=\"module\" src=\"/ChatService/js/market/conversation-page.js\">")
                .contains("/ChatService/css/market/market.css")
                .contains("id=\"conversationList\"")
                .contains("data-auth=\"required\"");
    }

    @Test
    void 비로그인_메인_화면은_로그인_아님과_빈_닉네임을_넘긴다() throws Exception {
        HttpResponse<String> page = get("/");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body())
                .contains("data-logged-in=\"false\" data-user-name=\"\"")
                .contains("<script type=\"module\" src=\"/ChatService/js/market/app.js\">");
    }

    @Test
    void 로그인한_메인_화면은_로그인_여부와_닉네임을_넘긴다() throws Exception {
        TestMember member = member("page");
        HttpResponse<String> page = get("/", member.cookieHeader());
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("data-logged-in=\"true\" data-user-name=\"" + member.nickname() + "\"");
    }

    @Test
    void 닉네임은_속성값으로_이스케이프된다() throws Exception {
        TestMember member = member("q\"<b>");
        HttpResponse<String> page = get("/", member.cookieHeader());
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body())
                .contains("data-user-name=\"" + member.nickname().replace("\"", "&#034;").replace("<", "&lt;").replace(">", "&gt;") + "\"")
                .doesNotContain(member.nickname());
    }

    @Test
    void 화면_모듈과_스타일이_올바른_형식으로_제공된다() throws Exception {
        for (String module : MODULES) {
            HttpResponse<String> response = get(module);
            assertThat(response.statusCode()).as(module).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).as(module).contains("javascript");
        }
        HttpResponse<String> css = get("/css/market/market.css");
        assertThat(css.statusCode()).isEqualTo(200);
        assertThat(css.headers().firstValue("Content-Type").orElse("")).contains("text/css");
        assertThat(get("/js/join/join.js").statusCode()).isEqualTo(200);
    }

    @Test
    void 가입_화면은_외부_스크립트_없이_가입_스크립트를_싣는다() throws Exception {
        HttpResponse<String> page = get("/members/join");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("/ChatService/js/join/join.js").doesNotContain("jquery").contains("id=\"formError\"");
    }
}
