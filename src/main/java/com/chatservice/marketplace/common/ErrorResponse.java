package com.chatservice.marketplace.common;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API 오류 응답 본문. 형식은 {code, status, message}이고 입력 오류일 때만 fieldErrors 를 더한다(설계 명세서 5.1절).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, int status, String message, Map<String, String> fieldErrors) {

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(errorCode.name(), errorCode.status().value(), message, null);
	}

	public static ErrorResponse of(ErrorCode errorCode, String message, Map<String, String> fieldErrors) {
		return new ErrorResponse(errorCode.name(), errorCode.status().value(), message,
				(fieldErrors == null || fieldErrors.isEmpty()) ? null : fieldErrors);
	}
}
