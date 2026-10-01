package com.chatservice.marketplace.conversation;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.common.MemberDirectory;
import com.chatservice.marketplace.conversation.realtime.RealtimeEvents.MessageEvent;
import com.chatservice.marketplace.conversation.realtime.RealtimePublisher;

@Service
public class MessageService implements IMessageService {

	private static final Logger log = LoggerFactory.getLogger(MessageService.class);

	private final ConversationAccess conversationAccess;
	private final ConversationStateEvaluator stateEvaluator;
	private final ChatMessageRepository messageRepository;
	private final MemberDirectory memberDirectory;
	private final RealtimePublisher realtimePublisher;
	private final Clock clock;

	public MessageService(ConversationAccess conversationAccess, ConversationStateEvaluator stateEvaluator,
			ChatMessageRepository messageRepository, MemberDirectory memberDirectory,
			RealtimePublisher realtimePublisher, Clock clock) {
		this.conversationAccess = conversationAccess;
		this.stateEvaluator = stateEvaluator;
		this.messageRepository = messageRepository;
		this.memberDirectory = memberDirectory;
		this.realtimePublisher = realtimePublisher;
		this.clock = clock;
	}

	/**
	 * 처리 순서(설계 명세서 6.6절)
	 * 1. 대화가 없으면 404, 참여자가 아니면 403 NOT_CONVERSATION_MEMBER
	 * 2. 읽기 전용이면 409 CONVERSATION_READ_ONLY
	 * 3. 메시지를 저장한다
	 * 4. 커밋 뒤 상대방 세션에 MESSAGE 이벤트를 보낸다. 전달 실패는 저장 결과에 영향을 주지 않는다
	 */
	@Override
	@Transactional
	public MessageResponse send(String memberId, Long conversationId, MessageSendRequest request) {
		Conversation conversation = conversationAccess.requireParticipant(memberId, conversationId);
		if (!stateEvaluator.evaluate(conversation).writable()) {
			throw new BusinessException(ErrorCode.CONVERSATION_READ_ONLY);
		}
		ChatMessage message = messageRepository.saveAndFlush(ChatMessage.write(conversationId, memberId,
				request.content(), request.requestId(), clock.instant()));
		String nickname = memberDirectory.nickname(memberId);
		log.info("메시지 저장 conversationId={} messageId={} memberId={}", conversationId, message.getMessageId(),
				memberId);
		realtimePublisher.publish(conversationId, new MessageEvent(message.getMessageId(), conversationId, memberId,
				nickname, message.getContent(), message.getCreatedAt()), memberId);
		return MessageResponse.of(message, nickname);
	}
}
