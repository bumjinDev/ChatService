package com.chatservice.marketplace.conversation.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.error.BusinessException;
import com.chatservice.marketplace.common.error.ErrorCode;
import com.chatservice.marketplace.common.member.MemberDirectory;
import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.conversation.ChatMessageRepository;
import com.chatservice.marketplace.conversation.domain.ChatMessage;
import com.chatservice.marketplace.conversation.domain.Conversation;
import com.chatservice.marketplace.conversation.dto.MessageResponse;
import com.chatservice.marketplace.conversation.dto.SentMessageResponse;
import com.chatservice.marketplace.conversation.realtime.RealtimePublisher;
import com.chatservice.marketplace.conversation.realtime.event.MessageEvent;

@Service
public class MessageService implements IMessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    private final ChatMessageRepository chatMessageRepository;
    private final IConversationService conversationService;
    private final MemberDirectory memberDirectory;
    private final RealtimePublisher realtimePublisher;
    private final TimeRules timeRules;

    public MessageService(ChatMessageRepository chatMessageRepository, IConversationService conversationService,
                          MemberDirectory memberDirectory, RealtimePublisher realtimePublisher, TimeRules timeRules) {
        this.chatMessageRepository = chatMessageRepository;
        this.conversationService = conversationService;
        this.memberDirectory = memberDirectory;
        this.realtimePublisher = realtimePublisher;
        this.timeRules = timeRules;
    }

    /*
     * 처리 순서(설계 6.6): 참여자 확인(404/403) → 쓰기 가능 판단(409) → 저장 → 커밋 후 상대방 세션에 전달.
     * 저장이 실패하면 예외가 나가므로 성공 응답과 실시간 전달이 일어나지 않는다.
     */
    @Override
    @Transactional
    public SentMessageResponse send(String memberId, Long conversationId, String content, String requestId) {
        Conversation conversation = conversationService.getForMember(memberId, conversationId);
        if (!conversationService.writability(conversation).writable()) {
            throw new BusinessException(ErrorCode.CONVERSATION_READ_ONLY);
        }
        ChatMessage message = chatMessageRepository.save(
                ChatMessage.write(conversationId, memberId, content, requestId, timeRules.now()));
        logger.info("[메시지 저장] conversationId={}, memberId={}, messageId={}",
                conversationId, memberId, message.getMessageId());
        realtimePublisher.publish(conversationId,
                MessageEvent.of(message.getMessageId(), conversationId, memberId, memberDirectory.nickname(memberId),
                        message.getContent(), message.getCreatedAt()),
                memberId);
        return new SentMessageResponse(message.getMessageId(), memberId, message.getContent(), message.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> list(String memberId, Long conversationId, Long afterId) {
        conversationService.getForMember(memberId, conversationId);
        List<ChatMessage> messages = (afterId == null)
                ? chatMessageRepository.findByConversationIdOrderByMessageIdAsc(conversationId)
                : chatMessageRepository.findByConversationIdAndMessageIdGreaterThanOrderByMessageIdAsc(conversationId, afterId);
        Set<String> senderIds = new HashSet<>();
        messages.forEach(message -> senderIds.add(message.getSenderId()));
        Map<String, String> nicknames = memberDirectory.nicknames(senderIds);
        return messages.stream()
                .map(message -> new MessageResponse(message.getMessageId(), message.getSenderId(),
                        nicknames.get(message.getSenderId()), message.getContent(), message.getCreatedAt()))
                .toList();
    }
}
