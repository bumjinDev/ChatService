package com.chatservice.user.exceptionhandler;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chatservice.user.exception.JwtKeyNotFoundException;
import com.chatservice.user.exception.MemberNotFoundException;
import com.chatservice.user.exception.NicknameAlreadyExistsException;


@Order(1)
@RestControllerAdvice(basePackages = "com.wherehouse.members.controller")
public class MemberAPIControllerExceptionHandler {		/* 클래스 'BindException' 상속 받음.  */
	
	private static final Logger logger = LoggerFactory.getLogger(MemberAPIControllerExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
    	
    	logger.info("MemberControllerExceptionHandler.handleValidationExceptions()!");
    	
        Map<String, String> errors = new HashMap<>();

        /**
         * @Valid 유효성 검증 실패 시 처리 로직
         *
         * - MethodArgumentNotValidException에서 getBindingResult()로 BindingResult 추출
         * - getFieldErrors()를 통해 유효성 실패 필드 목록(List<FieldError>) 조회
         *   • getField() → 실패한 필드명
         *   • getDefaultMessage() → 지정된 에러 메시지(@NotBlank 등)
         *
         * - 필드명과 메시지를 Map<String, String>에 저장하여 클라이언트에 JSON 응답
         * - JS 단에서는 각 필드 메시지를 기반으로 오류 표시 처리 (join.js, modify.js)
         *
         * - 유의사항:
         *   • @Valid 어노테이션은 어노테이션 종류와 관계없이 MethodArgumentNotValidException 발생
         *   • 오류 메시지는 FieldError.getDefaultMessage()를 통해 분기 가능
         */
        ex.getBindingResult().getFieldErrors().forEach(error -> {		
            errors.put(error.getField(), error.getDefaultMessage());
        });
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }
    
    /* ===== Serivce 빈에서 발생한 예외 처리 ==== */
    
    /**
     * 409 Conflict – 닉네임 중복
     */
    @ExceptionHandler(NicknameAlreadyExistsException.class)
    public ResponseEntity<?> handleNicknameDuplicate(NicknameAlreadyExistsException ex) {
//        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    	return buildResponse(HttpStatus.CONFLICT, "중복된 닉네임 입니다.");
    }

    /**
     * 404 Not Found – 회원을 찾을 수 없음
     */
    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<?> handleMemberNotFound(MemberNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * 401 Unauthorized – JWT 키 없음
     */
    @ExceptionHandler(JwtKeyNotFoundException.class)
    public ResponseEntity<?> handleJwtKeyNotFound(JwtKeyNotFoundException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * 공통 응답 포맷 생성, 서버는 이 응답인 에러 코드 및 메시지만 제공하고 실제 리다이렉트 등은 브라우저에서 이걸 받아서 처리한다.
     */
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("code", status.value());
        errorData.put("status", status.getReasonPhrase());
        errorData.put("message", message);
        return new ResponseEntity<>(errorData, status);
    }
}
