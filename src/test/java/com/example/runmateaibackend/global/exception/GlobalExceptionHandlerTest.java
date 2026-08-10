package com.example.runmateaibackend.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * GlobalExceptionHandler 단위 테스트.
 *
 * Spring 컨텍스트를 띄우지 않고 클래스를 직접 인스턴스화해서 각 핸들러 메서드를
 * 호출하는 순수 단위 테스트다. 오늘 401/403/404/409로 세분화한 예외 매핑이
 * 각각 정확한 HTTP 상태 코드와 메시지로 응답하는지 검증한다.
 */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	@DisplayName("IllegalArgumentException은 400 Bad Request로 응답한다")
	void handleIllegalArgumentException() {
		IllegalArgumentException exception = new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");

		ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getMessage()).isEqualTo("현재 비밀번호가 일치하지 않습니다.");
		assertThat(response.getBody().getStatus()).isEqualTo(400);
	}

	@Test
	@DisplayName("UnauthorizedException은 401 Unauthorized로 응답한다")
	void handleUnauthorizedException() {
		UnauthorizedException exception = new UnauthorizedException("이메일 또는 비밀번호가 올바르지 않습니다.");

		ResponseEntity<ErrorResponse> response = handler.handleUnauthorizedException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getMessage()).isEqualTo("이메일 또는 비밀번호가 올바르지 않습니다.");
	}

	@Test
	@DisplayName("ForbiddenException은 403 Forbidden으로 응답한다")
	void handleForbiddenException() {
		ForbiddenException exception = new ForbiddenException("본인의 기록만 수정할 수 있습니다.");

		ResponseEntity<ErrorResponse> response = handler.handleForbiddenException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getMessage()).isEqualTo("본인의 기록만 수정할 수 있습니다.");
	}

	@Test
	@DisplayName("ResourceNotFoundException은 404 Not Found로 응답한다")
	void handleResourceNotFoundException() {
		ResourceNotFoundException exception = new ResourceNotFoundException("유저를 찾을 수 없습니다.");

		ResponseEntity<ErrorResponse> response = handler.handleResourceNotFoundException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getMessage()).isEqualTo("유저를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("ConflictException은 409 Conflict로 응답한다")
	void handleConflictException() {
		ConflictException exception = new ConflictException("이미 사용 중인 이메일입니다.");

		ResponseEntity<ErrorResponse> response = handler.handleConflictException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getMessage()).isEqualTo("이미 사용 중인 이메일입니다.");
	}

	@Test
	@DisplayName("DataIntegrityViolationException은 409 Conflict로 응답하고, " +
		"원본 DB 에러 메시지 대신 사용자용 안내 메시지로 치환된다")
	void handleDataIntegrityViolationException() {
		DataIntegrityViolationException exception =
			new DataIntegrityViolationException("duplicate key value violates unique constraint \"one_active_plan_per_user\"");

		ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolationException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		// 원본 DB 예외 메시지가 그대로 노출되지 않고, 사용자 친화적인 메시지로 치환되어야 한다.
		assertThat(response.getBody().getMessage()).doesNotContain("constraint", "duplicate key");
		assertThat(response.getBody().getMessage()).isEqualTo("이미 처리 중인 요청이 있습니다. 잠시 후 다시 시도해주세요.");
	}

	@Test
	@DisplayName("예상하지 못한 그 외 예외는 500 Internal Server Error로 응답하고, " +
		"내부 예외 메시지가 그대로 노출되지 않는다")
	void handleUnexpectedException() {
		RuntimeException exception = new RuntimeException("NullPointerException at line 42 in PlanService.java");

		ResponseEntity<ErrorResponse> response = handler.handleException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getMessage()).isEqualTo("서버 오류가 발생했습니다.");
		// 내부 구현 세부사항(스택트레이스, 클래스명 등)이 클라이언트로 새어나가면 안 된다.
		assertThat(response.getBody().getMessage()).doesNotContain("PlanService", "NullPointerException");
	}
}