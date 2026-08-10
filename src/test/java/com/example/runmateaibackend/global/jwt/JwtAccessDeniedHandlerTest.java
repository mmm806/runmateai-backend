package com.example.runmateaibackend.global.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

/**
 * JwtAccessDeniedHandler 단위 테스트.
 *
 * 로그인은 했지만(인증은 됐지만) 권한이 부족해 접근이 거부된 경우
 * (예: 일반 유저가 /api/admin/**에 접근) 여기로 온다. JwtAuthenticationEntryPoint(401)와
 * 동일한 ErrorResponse 포맷을 쓰는지, 상태 코드만 403으로 정확히 다른지를 검증한다.
 */
class JwtAccessDeniedHandlerTest {

	private final JwtAccessDeniedHandler accessDeniedHandler = new JwtAccessDeniedHandler();

	@Test
	@DisplayName("권한이 부족한 요청에 대해 403 상태 코드와 JSON 형식의 에러 응답을 반환한다")
	void handle_forbiddenRequest_returns403WithJsonErrorBody() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		accessDeniedHandler.handle(request, response, new AccessDeniedException("권한 없음"));

		assertThat(response.getStatus()).isEqualTo(403);
		// setCharacterEncoding()이 이후에 호출되면 구현체에 따라 charset이 덧붙을 수 있어 startsWith로 확인한다.
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
		assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
		assertThat(response.getContentAsString())
			.contains("\"message\":\"접근 권한이 없습니다.\"")
			.contains("\"status\":403");
	}
}