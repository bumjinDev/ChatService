package com.chatservice.marketplace.common.error;

import org.springframework.http.HttpStatus;

/**
 * 업무 오류 코드.
 *
 * 각 값은 HTTP 상태 코드와 기본 메시지를 가진다.
 * 서비스가 {@link BusinessException}으로 던지면 {@link ApiExceptionHandler}가 {@code {code, status, message}} 형식의 JSON으로 응답한다.
 * 클라이언트는 HTTP 상태가 아니라 {@code code} 값으로 오류 원인을 구분한다.
 *
 * @see BusinessException
 * @see ApiExceptionHandler
 * @see ErrorResponse
 */
public enum ErrorCode {

    // ---- 400 Bad Request

    /** 입력값 검증 실패, 또는 금액·잔액이 저장 범위를 넘는다. 응답에 필드별 사유(fieldErrors)를 담을 수 있다. */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),

    // ---- 401 Unauthorized

    /** 인증 쿠키가 없거나 JWT 검증에 실패했다. ApiAuthenticationEntryPoint가 응답한다. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),

    // ---- 403 Forbidden

    /** 보안 체인의 인가 규칙이 요청을 거부했다. 현재 규칙은 인증 여부만 검사하므로 업무 요청에서는 발생하지 않는다. */
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    /** 판매자가 본인 상품에 대화를 시작하거나 구매하려 했다. */
    SELF_TRADE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "본인 상품에는 채팅을 시작하거나 구매할 수 없습니다."),
    /** 요청한 회원이 대화의 구매 희망자도 판매자도 아니다. */
    NOT_CONVERSATION_MEMBER(HttpStatus.FORBIDDEN, "대화 참여자가 아닙니다."),
    /** 구매자만 할 수 있는 요청(가격 제안, 구매 확정, 환불 요청)을 다른 회원이 보냈다. */
    NOT_BUYER(HttpStatus.FORBIDDEN, "구매자만 요청할 수 있습니다."),
    /** 판매자만 할 수 있는 요청(제안 응답, 발송 등록, 환불 응답)을 다른 회원이 보냈다. */
    NOT_SELLER(HttpStatus.FORBIDDEN, "판매자만 요청할 수 있습니다."),
    /** 주문의 구매자도 판매자도 아닌 회원이 주문을 조회하거나 취소하려 했다. */
    NOT_TRADE_PARTY(HttpStatus.FORBIDDEN, "거래 당사자가 아닙니다."),

    // ---- 404 Not Found

    /** 상품이 없다. 공개 상세 조회에서는 판매 종료 상품도 이 코드로 응답한다. */
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    /** 대화가 없다. */
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "대화를 찾을 수 없습니다."),
    /** 가격 제안이 없다. */
    OFFER_NOT_FOUND(HttpStatus.NOT_FOUND, "가격 제안을 찾을 수 없습니다."),
    /** 주문이 없다. */
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    /** 판매자 환불 응답에서 주문 또는 환불 요청이 없다. */
    REFUND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "환불 요청을 찾을 수 없습니다."),

    // ---- 409 Conflict: 현재 상태에서 허용되지 않는 요청

    /** 판매 종료 상품에 새 대화, 가격 제안, 제안 응답, 구매를 요청했다. */
    PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "이미 판매 종료된 상품입니다."),
    /** 구매 수량이 상품의 남은 수량보다 많다. */
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "남은 수량보다 많이 구매할 수 없습니다."),
    /** 구매자의 지갑이 없거나 잔액이 총 결제액보다 적다. */
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "잔액이 부족합니다."),
    /** 결제에 선택한 제안이 없거나, 수락되지 않았거나, 다른 상품 또는 다른 구매자의 제안이다. */
    INVALID_OFFER_SELECTION(HttpStatus.CONFLICT, "선택한 합의 가격을 사용할 수 없습니다."),
    /** 대화에 응답 대기(PENDING) 제안이 이미 있는데 새로 제안했다. */
    OFFER_PENDING_EXISTS(HttpStatus.CONFLICT, "응답을 기다리는 가격 제안이 이미 있습니다."),
    /** 대화에 수락(ACCEPTED)된 제안이 있는데 새로 제안했다. 수락 뒤 재협상은 없다. */
    OFFER_ALREADY_ACCEPTED(HttpStatus.CONFLICT, "이미 수락된 가격 제안이 있어 다시 제안할 수 없습니다."),
    /** 이미 수락하거나 거절한 제안에 다시 응답했다. */
    OFFER_ALREADY_RESPONDED(HttpStatus.CONFLICT, "이미 응답한 가격 제안입니다."),
    /** 읽기 전용 대화에 메시지를 보냈다. */
    CONVERSATION_READ_ONLY(HttpStatus.CONFLICT, "읽기 전용 대화에는 메시지를 보낼 수 없습니다."),
    /** 발송 기한이 지난 주문에 발송 정보를 등록하려 했다. */
    SHIPMENT_DEADLINE_PASSED(HttpStatus.CONFLICT, "발송 기한이 지났습니다."),
    /** 발송 정보가 이미 등록된 주문에 다시 등록하려 했다. */
    ALREADY_SHIPPED(HttpStatus.CONFLICT, "발송 정보가 이미 등록되어 있습니다."),
    /** 최종 상태(정상 완료, 환불 완료, 취소 완료)인 주문에 발송 정보를 등록하려 했다. */
    ORDER_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 주문이 아닙니다."),
    /** 발송 대기가 아닌 주문(배송 중, 배송 완료)을 취소하려 했다. */
    ORDER_ALREADY_SHIPPED(HttpStatus.CONFLICT, "발송 이후에는 주문을 취소할 수 없습니다."),
    /** 배송 완료 전에 구매 확정이나 환불 요청을 했다. */
    ORDER_NOT_DELIVERED(HttpStatus.CONFLICT, "아직 배송이 완료되지 않았습니다."),
    /** 환불 요청으로 보류 중인 주문에 구매 확정을 했다. */
    ORDER_ON_HOLD(HttpStatus.CONFLICT, "환불 요청으로 보류 중인 주문입니다."),
    /** 상품 확인 기한(배송 완료 후 48시간)이 지난 뒤 구매 확정이나 환불 요청을 했다. */
    INSPECTION_PERIOD_ENDED(HttpStatus.CONFLICT, "상품 확인 기간이 끝났습니다."),
    /** 환불 요청이 이미 접수된 주문에 다시 환불을 요청했다. */
    REFUND_ALREADY_REQUESTED(HttpStatus.CONFLICT, "이미 환불 요청이 접수된 주문입니다."),
    /** 판매자가 이미 판단했거나 자동 승인된 환불 요청에 응답했다. */
    REFUND_ALREADY_DECIDED(HttpStatus.CONFLICT, "이미 처리된 환불 요청입니다."),
    /** 판매자 응답 기한(환불 접수 후 48시간)이 지난 뒤 응답했다. */
    REFUND_RESPONSE_DEADLINE_PASSED(HttpStatus.CONFLICT, "판매자 응답 기한이 지났습니다."),
    /** 보류 중이 아닌 주문에 판매자가 환불 응답을 했다. */
    ORDER_NOT_ON_HOLD(HttpStatus.CONFLICT, "보류 중인 주문이 아닙니다."),
    /** 최종 상태(정상 완료, 환불 완료, 취소 완료)인 주문을 취소하거나, 구매 확정하거나, 환불 요청했다. */
    TRADE_ALREADY_FINALIZED(HttpStatus.CONFLICT, "이미 종료된 거래입니다.");

    /** 응답에 사용할 HTTP 상태 코드 */
    private final HttpStatus status;
    /** 서비스가 메시지를 따로 지정하지 않을 때 응답에 사용하는 기본 메시지 */
    private final String defaultMessage;

    /**
     * @param status         응답에 사용할 HTTP 상태 코드
     * @param defaultMessage 기본 메시지
     */
    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    /** @return 응답에 사용할 HTTP 상태 코드 */
    public HttpStatus getStatus() {
        return status;
    }

    /** @return 기본 메시지 */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
