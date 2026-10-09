package com.chatservice.marketplace.common.error;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 오류 응답 본문. 기존 회원 API 의 {code, status, message} 형식을 유지하고,
 * code 에는 HTTP 상태 숫자 대신 업무 오류 코드 문자열을 넣는다(설계 5.1).
 * fieldErrors 는 입력 검증 실패일 때만 포함한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, int status, String message, Map<String, String> fieldErrors) {

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), errorCode.getStatus().value(), message, null);
    }

    public static ErrorResponse validation(String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.getStatus().value(), message, fieldErrors);
    }
}
