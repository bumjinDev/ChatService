package com.chatservice.marketplace.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.chatservice.user.exception.JwtKeyNotFoundException;
import com.chatservice.user.exception.MemberNotFoundException;
import com.chatservice.user.exception.NicknameAlreadyExistsException;
import com.chatservice.user.exception.UserIdAlreadyExistsException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * REST 컨트롤러 전체(@RestController)의 예외를 {code, status, message[, fieldErrors]} 형식으로 바꾼다(설계 명세서 5.1절).
 * 회원 API(/members/join, /members/edit)의 입력 검증 실패와 회원 예외도 이 핸들러가 처리한다.
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
		ErrorCode code = ex.getErrorCode();
		return ResponseEntity.status(code.status())
				.body(ErrorResponse.of(code, ex.getMessage(), ex.getFieldErrors()));
	}

	/** @Valid 검증 실패: 필드별 사유를 fieldErrors 에 담는다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleNotValid(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return validation(fieldErrors);
	}

	/** 본문 JSON 을 읽지 못한 경우: 형식 오류, 열거형에 없는 값, 정수 필드의 소수 값 등. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
			String field = mapping.getPath().get(mapping.getPath().size() - 1).getFieldName();
			if (field != null) {
				fieldErrors.put(field, describeMappingError(mapping));
			}
		}
		if (fieldErrors.isEmpty()) {
			fieldErrors.put("body", "요청 본문의 JSON 형식이 올바르지 않습니다.");
		}
		return validation(fieldErrors);
	}

	/** 경로·쿼리 값의 타입이 맞지 않는 경우(예: category 에 없는 값, 숫자가 아닌 ID). */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return validation(Map.of(ex.getName(), "허용되지 않는 값입니다."));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
		return validation(Map.of(ex.getParameterName(), "필수 값입니다."));
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
		return validation(Map.of("request", "입력값이 올바르지 않습니다."));
	}

	// ---- 회원 기능(user 패키지)의 예외 ----

	@ExceptionHandler(UserIdAlreadyExistsException.class)
	public ResponseEntity<ErrorResponse> handleUserIdDuplicate(UserIdAlreadyExistsException ex) {
		return of(ErrorCode.USER_ID_ALREADY_EXISTS);
	}

	@ExceptionHandler(NicknameAlreadyExistsException.class)
	public ResponseEntity<ErrorResponse> handleNicknameDuplicate(NicknameAlreadyExistsException ex) {
		return of(ErrorCode.NICKNAME_ALREADY_EXISTS);
	}

	@ExceptionHandler(MemberNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleMemberNotFound(MemberNotFoundException ex) {
		return of(ErrorCode.MEMBER_NOT_FOUND);
	}

	@ExceptionHandler(JwtKeyNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleJwtKeyNotFound(JwtKeyNotFoundException ex) {
		return of(ErrorCode.UNAUTHENTICATED);
	}

	/** 처리하지 못한 예외는 내부 정보를 노출하지 않고 500 으로 응답한다. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
		log.error("처리하지 못한 예외", ex);
		return of(ErrorCode.INTERNAL_ERROR);
	}

	private ResponseEntity<ErrorResponse> validation(Map<String, String> fieldErrors) {
		ErrorCode code = ErrorCode.VALIDATION_ERROR;
		return ResponseEntity.status(code.status())
				.body(ErrorResponse.of(code, code.defaultMessage(), fieldErrors));
	}

	private ResponseEntity<ErrorResponse> of(ErrorCode code) {
		return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, code.defaultMessage()));
	}

	private String describeMappingError(JsonMappingException mapping) {
		if (mapping instanceof InvalidFormatException invalid && invalid.getTargetType() != null
				&& invalid.getTargetType().isEnum()) {
			return "허용되지 않는 값입니다.";
		}
		return "형식이 올바르지 않습니다.";
	}
}
