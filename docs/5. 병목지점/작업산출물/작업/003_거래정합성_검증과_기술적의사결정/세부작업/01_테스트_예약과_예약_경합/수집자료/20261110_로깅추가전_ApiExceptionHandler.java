package com.chatservice.marketplace.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * REST 컨트롤러의 예외를 {code, status, message[, fieldErrors]} 형식으로 바꾼다(설계 5.1, 6.20).
 *
 * 적용 대상을 @RestController 로 한정한다. JSP 를 돌려주는 @Controller 의 오류 처리는 기존 방식을 그대로 둔다.
 * 기존 회원 API(/members/join, /members/edit)의 @Valid 실패도 이 핸들러가 처리한다.
 * 여기서 다루지 않는 예외는 Spring Boot 기본 오류 처리(500)로 넘어간다.
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        logger.info("[업무 오류] code={}, message={}", code, ex.getMessage());
        ErrorResponse body = (code == ErrorCode.VALIDATION_ERROR)
                ? ErrorResponse.validation(ex.getMessage(), ex.getFieldErrors())
                : ErrorResponse.of(code, ex.getMessage());
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        // fieldErrors 를 읽지 않는 클라이언트도 원인을 보여 줄 수 있도록 필드 메시지를 message 에도 이어 담는다.
        String message = fieldErrors.isEmpty()
                ? ErrorCode.VALIDATION_ERROR.getDefaultMessage()
                : fieldErrors.values().stream().collect(Collectors.joining("\n"));
        return validation(message, fieldErrors);
    }

    /**
     * JSON 을 읽지 못한 경우. 숫자 필드에 소수·문자·저장 범위를 넘는 값이 들어오거나 열거형 값이 틀린 경우가 여기에 온다.
     * accept-float-as-int=false 설정으로 소수는 잘리지 않고 이 예외가 된다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (ex.getCause() instanceof JsonMappingException mappingException && !mappingException.getPath().isEmpty()) {
            String field = mappingException.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));
            fieldErrors.put(field, "형식이 올바르지 않습니다.");
        }
        return validation("요청 본문의 형식이 올바르지 않습니다.", fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validation("요청 값의 형식이 올바르지 않습니다.", Map.of(ex.getName(), "형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return validation("필수 요청 값이 없습니다.", Map.of(ex.getParameterName(), "필수 값입니다."));
    }

    private ResponseEntity<ErrorResponse> validation(String message, Map<String, String> fieldErrors) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ErrorResponse.validation(message, fieldErrors));
    }
}
