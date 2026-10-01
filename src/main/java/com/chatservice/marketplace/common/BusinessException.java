package com.chatservice.marketplace.common;

import java.util.Collections;
import java.util.Map;

/**
 * 업무 규칙 위반을 알리는 예외. 서비스 계층에서 던지고 ApiExceptionHandler 가 응답으로 바꾼다.
 * 입력값 오류를 서비스에서 판단한 경우(예: 제안 금액이 등록 가격 이상)에는 fieldErrors 를 함께 담는다.
 */
public class BusinessException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final ErrorCode errorCode;
	private final Map<String, String> fieldErrors;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.defaultMessage(), Collections.emptyMap());
	}

	public BusinessException(ErrorCode errorCode, String message) {
		this(errorCode, message, Collections.emptyMap());
	}

	public BusinessException(ErrorCode errorCode, String message, Map<String, String> fieldErrors) {
		super(message);
		this.errorCode = errorCode;
		this.fieldErrors = fieldErrors;
	}

	/** 한 필드의 입력 오류를 VALIDATION_ERROR 로 만든다. */
	public static BusinessException invalidField(String field, String reason) {
		return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(),
				Map.of(field, reason));
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public Map<String, String> getFieldErrors() {
		return fieldErrors;
	}
}
