package com.example.runmateaibackend.global.jwt;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.example.runmateaibackend.global.exception.ErrorResponse;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void commence(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException authException
	) throws IOException {
		// 토큰이 없거나 유효하지 않은 상태로 보호된 API에 접근했을 때 여기로 온다.
		// GlobalExceptionHandler가 만드는 ErrorResponse와 동일한 포맷으로 응답해서,
		// 프론트엔드가 필터 레벨/서비스 레벨 401을 구분 없이 같은 방식으로 처리할 수 있게 한다.
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");

		ErrorResponse errorResponse = new ErrorResponse("인증이 필요합니다.", HttpStatus.UNAUTHORIZED.value());
		response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
	}
}