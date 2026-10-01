package com.chatservice.marketplace.common;

import org.springframework.http.HttpStatus;

/**
 * 업무 오류 코드 목록(설계 명세서 6.20절).
 * 코드마다 HTTP 상태와 기본 메시지를 가진다. 서비스는 BusinessException 으로 던지고
 * ApiExceptionHandler 가 {code, status, message} 형식으로 바꿔 응답한다.
 */
public enum ErrorCode {

	// 400
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),

	// 401
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),

	// 403
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	SELF_TRADE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "본인이 등록한 상품에는 요청할 수 없습니다."),
	NOT_CONVERSATION_MEMBER(HttpStatus.FORBIDDEN, "대화 참여자가 아닙니다."),
	NOT_BUYER(HttpStatus.FORBIDDEN, "구매자만 요청할 수 있습니다."),
	NOT_SELLER(HttpStatus.FORBIDDEN, "판매자만 요청할 수 있습니다."),
	NOT_TRADE_PARTY(HttpStatus.FORBIDDEN, "거래 당사자가 아닙니다."),

	// 404
	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
	CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "대화를 찾을 수 없습니다."),
	OFFER_NOT_FOUND(HttpStatus.NOT_FOUND, "가격 제안을 찾을 수 없습니다."),
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
	REFUND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "환불 요청을 찾을 수 없습니다."),
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),

	// 409
	PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "이미 판매 종료된 상품입니다."),
	INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "잔액이 부족합니다."),
	INVALID_OFFER_SELECTION(HttpStatus.CONFLICT, "선택한 합의 가격을 사용할 수 없습니다."),
	OFFER_PENDING_EXISTS(HttpStatus.CONFLICT, "응답을 기다리는 가격 제안이 이미 있습니다."),
	OFFER_ALREADY_ACCEPTED(HttpStatus.CONFLICT, "수락된 가격 제안이 있어 다시 제안할 수 없습니다."),
	OFFER_ALREADY_RESPONDED(HttpStatus.CONFLICT, "이미 응답한 가격 제안입니다."),
	CONVERSATION_READ_ONLY(HttpStatus.CONFLICT, "읽기 전용 대화에는 메시지를 보낼 수 없습니다."),
	SHIPMENT_DEADLINE_PASSED(HttpStatus.CONFLICT, "발송 기한이 지났습니다."),
	ALREADY_SHIPPED(HttpStatus.CONFLICT, "발송 정보가 이미 등록되어 있습니다."),
	ORDER_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 주문이 아닙니다."),
	ORDER_ALREADY_SHIPPED(HttpStatus.CONFLICT, "발송 이후에는 주문을 취소할 수 없습니다."),
	ORDER_NOT_DELIVERED(HttpStatus.CONFLICT, "아직 배송이 완료되지 않았습니다."),
	ORDER_ON_HOLD(HttpStatus.CONFLICT, "환불 요청으로 거래가 보류 중입니다."),
	INSPECTION_PERIOD_ENDED(HttpStatus.CONFLICT, "상품 확인 기간이 끝났습니다."),
	REFUND_ALREADY_REQUESTED(HttpStatus.CONFLICT, "이미 접수된 환불 요청이 있습니다."),
	REFUND_ALREADY_DECIDED(HttpStatus.CONFLICT, "이미 처리된 환불 요청입니다."),
	REFUND_RESPONSE_DEADLINE_PASSED(HttpStatus.CONFLICT, "환불 응답 기한이 지났습니다."),
	ORDER_NOT_ON_HOLD(HttpStatus.CONFLICT, "보류 중인 주문이 아닙니다."),
	TRADE_ALREADY_FINALIZED(HttpStatus.CONFLICT, "이미 종료된 거래입니다."),
	USER_ID_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
	NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

	// 500
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다.");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultMessage() {
		return defaultMessage;
	}
}
