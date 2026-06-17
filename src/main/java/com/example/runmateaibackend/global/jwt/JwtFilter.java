package com.example.runmateaibackend.global.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
	// OncePerRequestFilter → 요청당 딱 한 번만 실행되는 필터

	private final JwtUtil jwtUtil;
	private final UserDetailsService userDetailsService;

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {

		// 1. 요청 헤더에서 토큰 추출
		String token = resolveToken(request);

		// 2. 토큰이 있고 유효하면 인증 처리
		if (token != null && jwtUtil.validateToken(token)) {

			// 3. 토큰에서 이메일 추출
			String email = jwtUtil.getEmailFromToken(token);

			// 4. 이메일로 유저 정보 조회
			UserDetails userDetails = userDetailsService.loadUserByUsername(email);

			// 5. 인증 객체 생성 후 SecurityContext에 저장
			// → 이후 Controller에서 인증된 유저 정보 사용 가능
			UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(
					userDetails, null, userDetails.getAuthorities()
				);
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}

		// 6. 다음 필터로 요청 전달
		filterChain.doFilter(request, response);
	}

	// Authorization 헤더에서 Bearer 토큰 추출
	private String resolveToken(HttpServletRequest request) {
		String bearerToken = request.getHeader("Authorization");
		// "Bearer eyJhbGci..." 에서 "eyJhbGci..." 부분만 추출
		if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7);
		}
		return null;
	}
}