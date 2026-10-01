package com.chatservice.marketplace.conversation;

/** 채팅 시작 결과. created 가 true 이면 새로 만든 대화(201), false 이면 기존 대화(200)다. */
public record ConversationStartResult(boolean created, ConversationDetailResponse conversation) {
}
