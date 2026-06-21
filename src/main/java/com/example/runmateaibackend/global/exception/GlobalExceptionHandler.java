package com.example.runmateaibackend.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	// 비즈니스 로직 예외 (예: "프로필을 먼저 등록해주세요.")
	// → 400 Bad Request로 명확하게 응답
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
		ErrorResponse response = new ErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST.value());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}

	// @Valid 검증 실패 시 (예: 이메일 형식 오류, 필수값 누락 등)
	// → 400 Bad Request + 첫 번째 검증 오류 메시지
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(error -> error.getDefaultMessage())
			.orElse("입력값이 올바르지 않습니다.");

		ErrorResponse response = new ErrorResponse(message, HttpStatus.BAD_REQUEST.value());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}

	// 그 외 예상하지 못한 모든 예외
	// → 500 Internal Server Error
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(Exception e) {
		ErrorResponse response = new ErrorResponse("서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR.value());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
	}
}