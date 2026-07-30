package com.example.runmateaibackend.global.jwt;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.example.runmateaibackend.global.exception.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

// 인증은 됐지만(로그인은 했지만) 권한이 부족해 접근이 거부됐을 때 여기로 온다.
// 예: 일반 유저(ROLE_USER)가 /api/admin/** 에 접근한 경우.
// JwtAuthenticationEntryPoint(401)와 동일한 ErrorResponse 포맷으로 응답을 통일한다.
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void handle(
		HttpServletRequest request,
		HttpServletResponse response,
		AccessDeniedException accessDeniedException
	) throws IOException {
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");

		ErrorResponse errorResponse = new ErrorResponse("접근 권한이 없습니다.", HttpStatus.FORBIDDEN.value());
		response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
	}
}