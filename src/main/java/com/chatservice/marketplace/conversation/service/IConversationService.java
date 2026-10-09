package com.chatservice.marketplace.conversation.service;

import java.time.Instant;
import java.util.List;

import com.chatservice.marketplace.conversation.domain.Conversation;
import com.chatservice.marketplace.conversation.domain.ConversationWritability;
import com.chatservice.marketplace.conversation.dto.ConversationDetailResponse;
import com.chatservice.marketplace.conversation.dto.ConversationSummaryResponse;
import com.chatservice.marketplace.product.domain.Product;

public interface IConversationService {

    /** F-005 채팅 시작·이어가기. 기존 대화가 있으면 created=false 로 그 대화를 돌려준다. */
    StartResult start(String memberId, Long productId);

    /** F-007 내 채팅 목록. */
    List<ConversationSummaryResponse> list(String memberId);

    /** F-007 대화 상세·거래 표시. */
    ConversationDetailResponse detail(String memberId, Long conversationId);

    /** 대화를 읽고 요청한 회원이 참여자인지 확인한다. 없으면 404, 참여자가 아니면 403 NOT_CONVERSATION_MEMBER. */
    Conversation getForMember(String memberId, Long conversationId);

    /** WebSocket 핸드셰이크의 참여자 검사. 없는 대화는 false. */
    boolean isParticipant(Long conversationId, String memberId);

    /** BR-007 쓰기 가능 여부(상품·당사자 주문 상태로 계산). */
    ConversationWritability writability(Conversation conversation);

    /** 결제 성공 시 실제 구매자·판매자 대화가 없으면 만든다(createdBySystem=1). 호출하는 쪽 트랜잭션에서 실행된다. */
    void ensureTradeConversation(Product product, String buyerId, Instant now);

    /** 재고가 바뀌었을 때 상품의 모든 대화에 대화별 CONVERSATION_STATE 를 보낸다(커밋 후 전송). */
    void publishStateToProductConversations(Product product);

    /** 주문이 최종 상태가 되었을 때 그 주문 당사자의 대화에만 CONVERSATION_STATE 를 보낸다(커밋 후 전송). */
    void publishStateToTradeConversation(Long productId, String buyerId);

    record StartResult(boolean created, ConversationDetailResponse detail) {
    }
}
