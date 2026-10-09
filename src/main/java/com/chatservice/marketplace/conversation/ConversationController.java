package com.chatservice.marketplace.conversation;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chatservice.marketplace.conversation.dto.ConversationDetailResponse;
import com.chatservice.marketplace.conversation.dto.ConversationSummaryResponse;
import com.chatservice.marketplace.conversation.dto.MessageResponse;
import com.chatservice.marketplace.conversation.dto.SendMessageRequest;
import com.chatservice.marketplace.conversation.dto.SentMessageResponse;
import com.chatservice.marketplace.conversation.service.IConversationService;
import com.chatservice.marketplace.conversation.service.IMessageService;

import jakarta.validation.Valid;

/** 상품별 대화와 메시지(설계 5.2.6~5.2.10). */
@RestController
@RequestMapping("/api")
public class ConversationController {

    private final IConversationService conversationService;
    private final IMessageService messageService;

    public ConversationController(IConversationService conversationService, IMessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    /** 새로 만들면 201, 기존 대화를 이어가면 200. */
    @PostMapping("/products/{productId}/conversations")
    public ResponseEntity<ConversationDetailResponse> start(@AuthenticationPrincipal String memberId,
                                                            @PathVariable("productId") Long productId) {
        IConversationService.StartResult result = conversationService.start(memberId, productId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.detail());
    }

    @GetMapping("/conversations")
    public List<ConversationSummaryResponse> list(@AuthenticationPrincipal String memberId) {
        return conversationService.list(memberId);
    }

    @GetMapping("/conversations/{conversationId}")
    public ConversationDetailResponse detail(@AuthenticationPrincipal String memberId,
                                             @PathVariable("conversationId") Long conversationId) {
        return conversationService.detail(memberId, conversationId);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<MessageResponse> messages(@AuthenticationPrincipal String memberId,
                                          @PathVariable("conversationId") Long conversationId,
                                          @RequestParam(name = "afterId", required = false) Long afterId) {
        return messageService.list(memberId, conversationId, afterId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<SentMessageResponse> send(@AuthenticationPrincipal String memberId,
                                                    @PathVariable("conversationId") Long conversationId,
                                                    @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.send(memberId, conversationId, request.content(), request.requestId()));
    }
}
