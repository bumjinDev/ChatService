package com.chatservice.marketplace.conversation.realtime;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 저장이 끝난 사건을 대화의 열린 세션에 JSON 으로 보낸다(설계 5.3, 7.2).
 *
 * - 호출 시점에 트랜잭션이 진행 중이면 커밋된 뒤에 보낸다. 커밋되지 않은 내용을 먼저 알리지 않기 위해서다.
 *   이벤트 본문은 호출 시점(트랜잭션 안)에 만들어 두고 전송만 미룬다.
 * - 전송은 한 번만 시도한다. 실패하면 로그만 남기고 저장 결과에는 영향이 없다. 놓친 내용은 클라이언트가
 *   대화 상세·메시지 조회로 다시 가져온다.
 * - 같은 세션에 여러 스레드가 동시에 보내는 경우의 직렬화는 하지 않는다. 그런 전송이 실패하면 위와 같이 로그만 남는다.
 */
@Component
public class RealtimePublisher {

    private static final Logger logger = LoggerFactory.getLogger(RealtimePublisher.class);

    private final ConversationSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public RealtimePublisher(ConversationSessionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * 이벤트를 대화 참여자의 열린 WebSocket 세션에 보낸다.
     *
     * 이벤트는 호출 시점에 JSON으로 변환한다. 트랜잭션이 진행 중이면 전송 작업을 {@code afterCommit}에 등록해
     * 커밋이 끝난 뒤 보내고, 롤백되면 보내지 않는다. 트랜잭션이 없으면 바로 보낸다.
     *
     * @param conversationId  대상 대화 ID
     * @param event           보낼 이벤트 객체
     * @param excludeMemberId 받지 않을 회원 ID. null이면 모든 참여자에게 보낸다.
     */
    public void publish(Long conversationId, Object event, String excludeMemberId) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            logger.error("[실시간 전달] 이벤트 직렬화 실패 conversationId={}", conversationId, e);
            return;
        }
        Runnable send = () -> deliver(conversationId, payload, excludeMemberId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    /** 상품 재고가 바뀌었을 때 그 상품의 모든 대화에 대화별로 계산한 상태 이벤트를 보낸다. */
    public void publishToProductConversations(Long productId, Map<Long, ?> eventsByConversation) {
        logger.info("[실시간 전달] 상품 대화 상태 전달 productId={}, conversations={}", productId, eventsByConversation.size());
        eventsByConversation.forEach((conversationId, event) -> publish(conversationId, event, null));
    }

    private void deliver(Long conversationId, String payload, String excludeMemberId) {
        for (Map.Entry<String, WebSocketSession> entry : registry.sessionsOf(conversationId).entrySet()) {
            if (entry.getKey().equals(excludeMemberId)) {
                continue;
            }
            WebSocketSession session = entry.getValue();
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (Exception e) {
                logger.warn("[실시간 전달] 전송 실패 conversationId={}, memberId={}, reason={}",
                        conversationId, entry.getKey(), e.getMessage());
            }
        }
    }
}
