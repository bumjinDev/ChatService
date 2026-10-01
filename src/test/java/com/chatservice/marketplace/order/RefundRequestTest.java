package com.chatservice.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.chatservice.marketplace.common.ErrorCode;
import com.chatservice.marketplace.conversation.Conversation;
import com.chatservice.marketplace.conversation.IMessageService;
import com.chatservice.marketplace.conversation.MessageSendRequest;
import com.chatservice.marketplace.support.OrderTestSupport;
import com.chatservice.marketplace.wallet.TransactionType;

/** F-017 환불 요청·거래 보류 검증. */
class RefundRequestTest extends OrderTestSupport {

	@Autowired
	private IRefundService refundService;

	@Autowired
	private ITradeCompletionService completionService;

	@Autowired
	private RefundRequestRepository refundRequestRepository;

	@Autowired
	private IMessageService messageService;

	private RefundRequestCreateRequest request(String detail) {
		return new RefundRequestCreateRequest(RefundReasonCode.DESCRIPTION_MISMATCH, detail, null);
	}

	@Test
	void F017_AC01_47시간_59분에_유효한_환불을_요청하면_ON_HOLD_요청_PENDING_응답_기한이_채워진다() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		clock.set(order.getDeliveredAt().plus(Duration.ofHours(47)).plus(Duration.ofMinutes(59)));
		Instant now = clock.instant();

		mockMvc.perform(post("/api/orders/" + order.getOrderId() + "/refund-request").cookie(authCookie(buyer))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonCode\":\"DESCRIPTION_MISMATCH\",\"detail\":\"사진과 색상이 다릅니다\",\"requestId\":\"r-1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.tradeStatus").value("ON_HOLD"))
				.andExpect(jsonPath("$.refundRequest.reasonCode").value("DESCRIPTION_MISMATCH"))
				.andExpect(jsonPath("$.refundRequest.detail").value("사진과 색상이 다릅니다"))
				.andExpect(jsonPath("$.refundRequest.decision").value("PENDING"))
				.andExpect(jsonPath("$.refundRequest.requestedAt").value(now.toString()))
				.andExpect(jsonPath("$.refundRequest.responseDeadlineAt")
						.value(now.plus(Duration.ofHours(48)).toString()));

		assertThat(refundRequestRepository.findAll()).singleElement().satisfies(r -> {
			assertThat(r.getBuyerId()).isEqualTo(buyer);
			assertThat(r.getRequestId()).isEqualTo("r-1");
			assertThat(r.getDecidedAt()).isNull();
		});
		assertThat(transactions(seller, TransactionType.SALE_PAYOUT)).isEmpty();
		// 보류 후에는 정상 수령 확인으로 판매대금을 지급할 수 없다.
		assertThat(errorOf(() -> completionService.confirmReceipt(buyer, order.getOrderId())))
				.isEqualTo(ErrorCode.ORDER_ON_HOLD);
	}

	@Test
	void F017_AC02_정확히_48시간에_요청하면_409_INSPECTION_PERIOD_ENDED() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		clock.set(order.getDeliveredAt().plus(Duration.ofHours(48)));

		assertThat(errorOf(() -> refundService.requestRefund(buyer, order.getOrderId(), request("다름"))))
				.isEqualTo(ErrorCode.INSPECTION_PERIOD_ENDED);
		assertThat(refundRequestRepository.count()).isZero();
		assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getTradeStatus())
				.isEqualTo(TradeStatus.IN_PROGRESS);
	}

	@Test
	void F017_AC03_순차_접수한_뒤_다시_요청하면_409_REFUND_ALREADY_REQUESTED_이고_requestedAt_이_유지된다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		clock.advance(Duration.ofHours(1));
		Instant firstAt = clock.instant();
		refundService.requestRefund(buyer, order.getOrderId(), request("처음"));
		clock.advance(Duration.ofHours(2));

		assertThat(errorOf(() -> refundService.requestRefund(buyer, order.getOrderId(), request("다시"))))
				.isEqualTo(ErrorCode.REFUND_ALREADY_REQUESTED);
		assertThat(refundRequestRepository.findAll()).singleElement().satisfies(r -> {
			assertThat(r.getRequestedAt()).isEqualTo(firstAt);
			assertThat(r.getResponseDeadlineAt()).isEqualTo(firstAt.plus(Duration.ofHours(48)));
			assertThat(r.getDetail()).isEqualTo("처음");
		});
	}

	@Test
	void 보류_중에도_실제_당사자_대화는_쓰기_가능하다() {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		refundService.requestRefund(buyer, order.getOrderId(), request("다름"));
		Conversation conv = conversationRepository.findByProductIdOrderByConversationIdAsc(order.getProductId())
				.get(0);

		assertThat(messageService.send(seller, conv.getConversationId(), new MessageSendRequest("확인해 볼게요", null))
				.messageId()).isNotNull();
	}

	@Test
	void 구매자가_아니면_403_배송_완료_전이면_409_종료된_거래면_409() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		OrderDetailResponse waiting = purchase(buyer, product(seller, 30_000));
		assertThat(errorOf(() -> refundService.requestRefund(buyer, waiting.orderId(), request("다름"))))
				.isEqualTo(ErrorCode.ORDER_NOT_DELIVERED);

		PurchaseOrder delivered = deliveredOrder(seller, buyer, 20_000);
		mockMvc.perform(post("/api/orders/" + delivered.getOrderId() + "/refund-request").cookie(authCookie(seller))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonCode\":\"DESCRIPTION_MISMATCH\",\"detail\":\"판매자 요청\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_BUYER"));

		completionService.confirmReceipt(buyer, delivered.getOrderId());
		assertThat(errorOf(() -> refundService.requestRefund(buyer, delivered.getOrderId(), request("다름"))))
				.isEqualTo(ErrorCode.TRADE_ALREADY_FINALIZED);
		assertThat(refundRequestRepository.count()).isZero();
	}

	@Test
	void 사유가_목록에_없거나_상세_설명이_비면_400() throws Exception {
		String seller = member("it_seller", "판매자");
		String buyer = member("it_buyer", "구매자");
		PurchaseOrder order = deliveredOrder(seller, buyer, 30_000);
		String url = "/api/orders/" + order.getOrderId() + "/refund-request";

		mockMvc.perform(post(url).cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonCode\":\"BROKEN\",\"detail\":\"파손\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.reasonCode").exists());
		mockMvc.perform(post(url).cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonCode\":\"DESCRIPTION_MISMATCH\",\"detail\":\" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.detail").exists());
		mockMvc.perform(post(url).cookie(authCookie(buyer)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"detail\":\"설명\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.reasonCode").exists());
		assertThat(refundRequestRepository.count()).isZero();
	}
}
