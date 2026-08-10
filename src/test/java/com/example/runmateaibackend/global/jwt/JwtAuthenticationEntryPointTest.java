package com.example.runmateaibackend.global.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * JwtAuthenticationEntryPoint 단위 테스트.
 *
 * 토큰이 없거나 무효한 상태로 보호된 API에 접근했을 때, GlobalExceptionHandler와
 * 동일한 ErrorResponse 포맷(message, status)으로 응답하는지가 핵심이다. 프론트엔드가
 * "필터 레벨에서 막힌 401"과 "서비스 레벨에서 발생한 401"을 구분 없이 같은 방식으로
 * 처리하려면, 이 포맷이 반드시 GlobalExceptionHandler의 응답 포맷과 일치해야 한다.
 */
class JwtAuthenticationEntryPointTest {

	private final JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint();

	@Test
	@DisplayName("인증되지 않은 요청에 대해 401 상태 코드와 JSON 형식의 에러 응답을 반환한다")
	void commence_unauthenticatedRequest_returns401WithJsonErrorBody() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		entryPoint.commence(request, response, new BadCredentialsException("인증 실패"));

		assertThat(response.getStatus()).isEqualTo(401);
		// setCharacterEncoding()이 이후에 호출되면 구현체에 따라 charset이 덧붙을 수 있어 startsWith로 확인한다.
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
		assertThat(response.getContentAsString())
			.contains("\"message\":\"인증이 필요합니다.\"")
			.contains("\"status\":401");
	}
}