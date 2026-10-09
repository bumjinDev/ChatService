package com.chatservice.marketplace.conversation.realtime.event;

import java.time.Instant;

/** 메시지 저장 직후 상대방 세션에 보내는 이벤트(설계 5.3 MESSAGE). */
public record MessageEvent(String type, Long messageId, Long conversationId, String senderId, String senderNickname,
                           String content, Instant createdAt) {

    public static MessageEvent of(Long messageId, Long conversationId, String senderId, String senderNickname,
                                  String content, Instant createdAt) {
        return new MessageEvent("MESSAGE", messageId, conversationId, senderId, senderNickname, content, createdAt);
    }
}
