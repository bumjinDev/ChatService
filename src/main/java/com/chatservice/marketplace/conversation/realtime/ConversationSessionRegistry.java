package com.chatservice.marketplace.conversation.realtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 대화 ID → 회원 ID → 열린 세션 하나를 보관한다. 기존 websocketcore 의 ChatSessionRegistry 를 바탕으로
 * 인원수, sessionKey, TTL, 빈 방 삭제 처리를 뺀 것이다. JVM 메모리에만 있고 재기동하면 사라진다.
 */
@Component
public class ConversationSessionRegistry {

	private final Map<Long, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

	/** 세션을 등록한다. 같은 회원의 기존 세션이 있으면 바꾸고 기존 세션을 돌려준다(없으면 null). */
	public WebSocketSession register(Long conversationId, String memberId, WebSocketSession session) {
		return sessions.computeIfAbsent(conversationId, id -> new ConcurrentHashMap<>()).put(memberId, session);
	}

	/** 현재 등록된 세션이 주어진 세션과 같을 때만 제거한다. 대체된 이전 세션의 종료 처리가 새 세션을 지우지 않게 한다. */
	public boolean remove(Long conversationId, String memberId, WebSocketSession session) {
		Map<String, WebSocketSession> members = sessions.get(conversationId);
		return members != null && members.remove(memberId, session);
	}

	/** 대화에 등록된 회원별 세션. 반환값은 복사본이다. */
	public Map<String, WebSocketSession> sessionsOf(Long conversationId) {
		Map<String, WebSocketSession> members = sessions.get(conversationId);
		return members == null ? Map.of() : Map.copyOf(members);
	}
}
