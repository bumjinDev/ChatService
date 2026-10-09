package com.chatservice.marketplace.conversation.realtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 대화 ID → 회원 ID → 열린 세션 하나(설계 7.2).
 *
 * 기존 ChatSessionRegistry 에서 인원수·sessionKey·TTL·빈 방 정리를 뺐다. 여러 요청 스레드가 같은 맵을 읽고 쓰므로
 * ConcurrentHashMap 을 쓴다. 이는 맵 자료구조의 스레드 안전성을 위한 것이며 업무 데이터의 동시성 제어가 아니다.
 * JVM 메모리에만 있으므로 재기동하면 비고, 클라이언트가 다시 연결한다.
 */
@Component
public class ConversationSessionRegistry {

    private final Map<Long, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /** 세션을 등록하고 같은 회원의 이전 세션이 있으면 돌려준다. */
    public WebSocketSession register(Long conversationId, String memberId, WebSocketSession session) {
        return sessions.computeIfAbsent(conversationId, key -> new ConcurrentHashMap<>()).put(memberId, session);
    }

    /**
     * 현재 등록된 세션이 닫힌 세션과 같을 때만 지운다.
     * 새 연결로 바뀐 직후 이전 세션의 종료 이벤트가 늦게 도착해 새 세션을 지우는 것을 막는다(설계 5.3).
     */
    public void remove(Long conversationId, String memberId, WebSocketSession session) {
        Map<String, WebSocketSession> members = sessions.get(conversationId);
        if (members == null) {
            return;
        }
        members.remove(memberId, session);
        if (members.isEmpty()) {
            sessions.remove(conversationId, members);
        }
    }

    /** 대화의 열린 세션(회원 ID → 세션) 복사본. */
    public Map<String, WebSocketSession> sessionsOf(Long conversationId) {
        Map<String, WebSocketSession> members = sessions.get(conversationId);
        return members == null ? Map.of() : Map.copyOf(members);
    }
}
