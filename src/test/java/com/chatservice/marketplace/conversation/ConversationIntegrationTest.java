package com.chatservice.marketplace.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chatservice.marketplace.support.IntegrationTestSupport;
import com.chatservice.marketplace.support.TestMember;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * F-005 채팅 시작·이어가기, F-006 메시지 전송, F-007 채팅 목록·내역 조회(설계 9장).
 * 결제로 재고가 0 이 된 뒤의 쓰기 가능 판단(F-006-AC-03, BR-007 여러 주문)은 주문 기능이 필요하므로
 * MultiQuantityIntegrationTest 에서 확인한다. F-005-AC-01(동시 시작), F-006-AC-02(재전송)는 후속 과제다.
 */
class ConversationIntegrationTest extends IntegrationTestSupport {

    @Test
    void F005_같은_회원이_같은_상품에_두_번_시작하면_201_뒤_200이고_같은_대화다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 50_000, 1);

        JsonNode first = json(postJson("/api/products/" + productId + "/conversations", buyer, null)
                .andExpect(status().isCreated()));
        JsonNode second = json(postJson("/api/products/" + productId + "/conversations", buyer, null)
                .andExpect(status().isOk()));
        assertThat(second.get("conversationId").asLong()).isEqualTo(first.get("conversationId").asLong());
        assertThat(first.get("myRole").asText()).isEqualTo("BUYER");
        assertThat(first.get("writable").asBoolean()).isTrue();
        assertThat(first.get("readOnlyReason").isNull()).isTrue();
        assertThat(first.get("buyer").get("nickname").asText()).isEqualTo(buyer.nickname());
        assertThat(first.get("seller").get("nickname").asText()).isEqualTo(seller.nickname());
        assertThat(count("SELECT COUNT(*) FROM CONVERSATION WHERE PRODUCT_ID = ?", productId)).isEqualTo(1);
    }

    @Test
    void F005_AC02_서로_다른_구매_희망자는_각각_별도_대화를_만든다() throws Exception {
        TestMember seller = member("seller");
        long productId = registerProduct(seller, 50_000, 1);
        long first = startConversation(member("buyerA"), productId);
        long second = startConversation(member("buyerB"), productId);
        assertThat(first).isNotEqualTo(second);
        assertThat(count("SELECT COUNT(*) FROM CONVERSATION WHERE PRODUCT_ID = ?", productId)).isEqualTo(2);
    }

    @Test
    void F005_본인_상품은_403_판매_종료_상품의_신규_대화는_409_없는_상품은_404다() throws Exception {
        TestMember seller = member("seller");
        long productId = registerProduct(seller, 50_000, 1);

        JsonNode self = json(postJson("/api/products/" + productId + "/conversations", seller, null)
                .andExpect(status().isForbidden()));
        assertThat(self.get("code").asText()).isEqualTo("SELF_TRADE_NOT_ALLOWED");

        jdbcTemplate.update("UPDATE PRODUCT SET REMAINING_QUANTITY = 0, STATUS = 'SOLD' WHERE PRODUCT_ID = ?", productId);
        JsonNode sold = json(postJson("/api/products/" + productId + "/conversations", member("buyer"), null)
                .andExpect(status().isConflict()));
        assertThat(sold.get("code").asText()).isEqualTo("PRODUCT_NOT_ON_SALE");

        postJson("/api/products/999999999/conversations", member("other"), null).andExpect(status().isNotFound());
        assertThat(count("SELECT COUNT(*) FROM CONVERSATION WHERE PRODUCT_ID = ?", productId)).isZero();
    }

    @Test
    void F005_기존_대화가_있으면_판매_종료_상품이라도_200으로_돌려준다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 50_000, 1);
        long conversationId = startConversation(buyer, productId);
        jdbcTemplate.update("UPDATE PRODUCT SET REMAINING_QUANTITY = 0, STATUS = 'SOLD' WHERE PRODUCT_ID = ?", productId);

        JsonNode existing = json(postJson("/api/products/" + productId + "/conversations", buyer, null)
                .andExpect(status().isOk()));
        assertThat(existing.get("conversationId").asLong()).isEqualTo(conversationId);
        assertThat(existing.get("product").get("status").asText()).isEqualTo("SOLD");
    }

    @Test
    void F006_빈_메시지는_400_비참여자는_403_없는_대화는_404이고_저장되지_않는다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long conversationId = startConversation(buyer, registerProduct(seller, 50_000, 1));

        for (String blank : new String[] {"", "   "}) {
            JsonNode error = json(postJson("/api/conversations/" + conversationId + "/messages", buyer,
                    Map.of("content", blank)).andExpect(status().isBadRequest()));
            assertThat(error.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        }
        postJson("/api/conversations/" + conversationId + "/messages", buyer, Map.of("content", "가".repeat(1001)))
                .andExpect(status().isBadRequest());
        JsonNode outsider = json(postJson("/api/conversations/" + conversationId + "/messages", member("outsider"),
                Map.of("content", "안녕하세요")).andExpect(status().isForbidden()));
        assertThat(outsider.get("code").asText()).isEqualTo("NOT_CONVERSATION_MEMBER");
        postJson("/api/conversations/999999999/messages", buyer, Map.of("content", "안녕하세요"))
                .andExpect(status().isNotFound());

        assertThat(count("SELECT COUNT(*) FROM CHAT_MESSAGE WHERE CONVERSATION_ID = ?", conversationId)).isZero();
    }

    @Test
    void F006_AC01_상대방이_접속하지_않았어도_저장된_메시지는_나중에_조회된다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long conversationId = startConversation(buyer, registerProduct(seller, 50_000, 1));

        JsonNode sent = json(postJson("/api/conversations/" + conversationId + "/messages", buyer,
                Map.of("content", "상품 상태 문의드립니다.")).andExpect(status().isCreated()));
        assertThat(sent.get("senderId").asText()).isEqualTo(buyer.id());

        JsonNode messages = json(getJson("/api/conversations/" + conversationId + "/messages", seller)
                .andExpect(status().isOk()));
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("messageId").asLong()).isEqualTo(sent.get("messageId").asLong());
        assertThat(messages.get(0).get("content").asText()).isEqualTo("상품 상태 문의드립니다.");
        assertThat(messages.get(0).get("senderNickname").asText()).isEqualTo(buyer.nickname());
    }

    @Test
    void F007_메시지는_messageId_오름차순이고_afterId_로_뒤의_메시지만_받는다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long conversationId = startConversation(buyer, registerProduct(seller, 50_000, 1));
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            ids.add(sendMessage(i % 2 == 0 ? seller : buyer, conversationId, "메시지 " + i));
        }

        JsonNode all = json(getJson("/api/conversations/" + conversationId + "/messages", buyer));
        assertThat(all).hasSize(5);
        for (int i = 0; i < 5; i++) {
            assertThat(all.get(i).get("messageId").asLong()).isEqualTo(ids.get(i));
        }

        JsonNode after = json(getJson("/api/conversations/" + conversationId + "/messages?afterId=" + ids.get(2), seller));
        assertThat(after).hasSize(2);
        assertThat(after.get(0).get("messageId").asLong()).isEqualTo(ids.get(3));
        assertThat(after.get(1).get("messageId").asLong()).isEqualTo(ids.get(4));
    }

    @Test
    void NFR002_연결이_끊긴_동안_저장된_3건을_afterId_로_다시_가져온다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long conversationId = startConversation(buyer, registerProduct(seller, 50_000, 1));
        long lastSeen = sendMessage(seller, conversationId, "마지막으로 본 메시지");
        for (int i = 0; i < 3; i++) {
            sendMessage(seller, conversationId, "놓친 메시지 " + i);
        }
        JsonNode missed = json(getJson("/api/conversations/" + conversationId + "/messages?afterId=" + lastSeen, buyer));
        assertThat(missed).hasSize(3);
    }

    @Test
    void F007_대화_목록은_참여한_대화만_역할과_상대_닉네임을_담아_돌려준다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long productId = registerProduct(seller, 50_000, 3);
        long conversationId = startConversation(buyer, productId);

        JsonNode sellerList = json(getJson("/api/conversations", seller).andExpect(status().isOk()));
        assertThat(sellerList).hasSize(1);
        assertThat(sellerList.get(0).get("conversationId").asLong()).isEqualTo(conversationId);
        assertThat(sellerList.get(0).get("myRole").asText()).isEqualTo("SELLER");
        assertThat(sellerList.get(0).get("counterpartNickname").asText()).isEqualTo(buyer.nickname());
        assertThat(sellerList.get(0).get("writable").asBoolean()).isTrue();
        assertThat(sellerList.get(0).get("product").get("remainingQuantity").asLong()).isEqualTo(3);

        JsonNode buyerList = json(getJson("/api/conversations", buyer));
        assertThat(buyerList.get(0).get("myRole").asText()).isEqualTo("BUYER");
        assertThat(json(getJson("/api/conversations", member("outsider")))).isEmpty();
    }

    @Test
    void F007_비참여자는_대화_상세와_메시지를_조회할_수_없고_없는_대화는_404이다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long conversationId = startConversation(buyer, registerProduct(seller, 50_000, 1));
        sendMessage(buyer, conversationId, "문의");
        TestMember outsider = member("outsider");

        JsonNode detail = json(getJson("/api/conversations/" + conversationId, outsider).andExpect(status().isForbidden()));
        assertThat(detail.get("code").asText()).isEqualTo("NOT_CONVERSATION_MEMBER");
        assertThat(detail.has("offers")).isFalse();
        getJson("/api/conversations/" + conversationId + "/messages", outsider).andExpect(status().isForbidden());
        JsonNode missing = json(getJson("/api/conversations/999999999", buyer).andExpect(status().isNotFound()));
        assertThat(missing.get("code").asText()).isEqualTo("CONVERSATION_NOT_FOUND");
    }

    @Test
    void F007_afterId_형식이_틀리면_400이다() throws Exception {
        TestMember seller = member("seller");
        TestMember buyer = member("buyer");
        long conversationId = startConversation(buyer, registerProduct(seller, 50_000, 1));
        getJson("/api/conversations/" + conversationId + "/messages?afterId=abc", buyer).andExpect(status().isBadRequest());
    }
}
