package com.chatservice.marketplace.conversation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** 대화 API(설계 명세서 5.2.6~5.2.10). */
@RestController
public class ConversationController {

	private final IConversationService conversationService;
	private final IMessageService messageService;

	public ConversationController(IConversationService conversationService, IMessageService messageService) {
		this.conversationService = conversationService;
		this.messageService = messageService;
	}

	/** 기존 대화가 있으면 200, 새로 만들면 201 이다. */
	@PostMapping("/api/products/{productId}/conversations")
	public ResponseEntity<ConversationDetailResponse> start(@AuthenticationPrincipal String memberId,
			@PathVariable("productId") Long productId) {
		ConversationStartResult result = conversationService.start(memberId, productId);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(result.conversation());
	}

	@PostMapping("/api/conversations/{conversationId}/messages")
	public ResponseEntity<MessageResponse> send(@AuthenticationPrincipal String memberId,
			@PathVariable("conversationId") Long conversationId, @Valid @RequestBody MessageSendRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(messageService.send(memberId, conversationId, request));
	}
}
