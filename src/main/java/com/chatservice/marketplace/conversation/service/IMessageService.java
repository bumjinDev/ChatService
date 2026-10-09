package com.chatservice.marketplace.conversation.service;

import java.util.List;

import com.chatservice.marketplace.conversation.dto.MessageResponse;
import com.chatservice.marketplace.conversation.dto.SentMessageResponse;

public interface IMessageService {

    /** F-006 메시지 전송. 저장 후 상대방의 열린 세션에 MESSAGE 를 보낸다. 호출마다 별도 메시지로 저장한다. */
    SentMessageResponse send(String memberId, Long conversationId, String content, String requestId);

    /** F-007 메시지 내역. afterId 가 있으면 그보다 큰 메시지만 돌려준다. */
    List<MessageResponse> list(String memberId, Long conversationId, Long afterId);
}
