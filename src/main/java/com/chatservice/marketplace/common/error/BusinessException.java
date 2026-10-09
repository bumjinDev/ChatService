package com.chatservice.marketplace.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 서비스가 업무 규칙 위반을 알릴 때 던지는 예외. ApiExceptionHandler 가 {code, status, message} 형식으로 바꾼다.
 * RuntimeException 이므로 @Transactional 메서드 안에서 던지면 그 요청의 DB 변경은 롤백된다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, String> fieldErrors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), Collections.emptyMap());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, Collections.emptyMap());
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, String> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    /** 입력값 하나가 업무 규칙(예: 등록 가격 이상 제안, 저장 범위 초과)에 맞지 않을 때 쓴다. */
    public static BusinessException invalidField(String field, String reason) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, reason, Map.of(field, reason));
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
