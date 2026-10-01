package com.chatservice.marketplace.conversation.realtime;

import java.io.IOException;
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
 * 대화 참여자의 열린 세션에 이벤트를 보낸다.
 * - 트랜잭션 안에서 호출되면 커밋이 끝난 뒤 보낸다. 저장되지 않은 내용은 전달하지 않는다.
 * - 전달은 한 번만 시도한다. 세션마다 전송 예외가 나면 로그만 남기고 다음 세션으로 넘어간다.
 * - excludeMemberId 의 세션은 뺀다. 시스템이 일으킨 사건은 null 을 넘겨 모든 참여자에게 보낸다.
 */
@Component
public class RealtimePublisher {

	private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

	private final ConversationSessionRegistry registry;
	private final ObjectMapper objectMapper;

	public RealtimePublisher(ConversationSessionRegistry registry, ObjectMapper objectMapper) {
		this.registry = registry;
		this.objectMapper = objectMapper;
	}

	public void publish(Long conversationId, Object event, String excludeMemberId) {
		String payload;
		try {
			payload = objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException e) {
			log.warn("이벤트 직렬화 실패 conversationId={} 원인={}", conversationId, e.getMessage());
			return;
		}
		Runnable send = () -> sendNow(conversationId, payload, excludeMemberId);
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

	private void sendNow(Long conversationId, String payload, String excludeMemberId) {
		for (Map.Entry<String, WebSocketSession> entry : registry.sessionsOf(conversationId).entrySet()) {
			if (entry.getKey().equals(excludeMemberId)) {
				continue;
			}
			WebSocketSession session = entry.getValue();
			if (!session.isOpen()) {
				continue;
			}
			try {
				// WebSocketSession 은 동시에 두 번 보낼 수 없으므로 세션 단위로 직렬화한다.
				synchronized (session) {
					session.sendMessage(new TextMessage(payload));
				}
			} catch (IOException | IllegalStateException e) {
				log.warn("이벤트 전달 실패 conversationId={} memberId={} 원인={}", conversationId, entry.getKey(),
						e.getMessage());
			}
		}
	}
}
