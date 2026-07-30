package com.example.runmateaibackend.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	// 순수 입력 검증 실패 (예: "현재 비밀번호가 일치하지 않습니다.")
	// → 400 Bad Request
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

	// 로그인 실패, 토큰 무효/만료 등 인증 실패
	// → 401 Unauthorized
	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ErrorResponse> handleUnauthorizedException(UnauthorizedException e) {
		ErrorResponse response = new ErrorResponse(e.getMessage(), HttpStatus.UNAUTHORIZED.value());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
	}

	// 인증은 됐지만 본인 소유가 아닌 리소스에 접근 시도
	// → 403 Forbidden
	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException e) {
		ErrorResponse response = new ErrorResponse(e.getMessage(), HttpStatus.FORBIDDEN.value());
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
	}

	// 요청한 리소스(유저, 기록, 플랜, 프로필 등)가 존재하지 않음
	// → 404 Not Found
	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException e) {
		ErrorResponse response = new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
	}

	// 중복 생성 시도, 또는 현재 서버 상태와 요청이 충돌 (예: 같은 날짜 기록 중복)
	// → 409 Conflict
	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ErrorResponse> handleConflictException(ConflictException e) {
		ErrorResponse response = new ErrorResponse(e.getMessage(), HttpStatus.CONFLICT.value());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
	}

	// DB 제약 조건 위반 (예: 동시 요청으로 인한 유니크 제약 충돌)
	// → 409 Conflict
	@ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
		org.springframework.dao.DataIntegrityViolationException e) {
		log.warn("데이터 제약 조건 위반: {}", e.getMessage());
		ErrorResponse response = new ErrorResponse(
			"이미 처리 중인 요청이 있습니다. 잠시 후 다시 시도해주세요.",
			HttpStatus.CONFLICT.value()
		);
		return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
	}

	// 그 외 예상하지 못한 모든 예외
	// → 500 Internal Server Error
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(Exception e) {
		log.error("처리되지 않은 예외 발생", e);
		ErrorResponse response = new ErrorResponse("서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR.value());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
	}
}