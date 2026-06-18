package com.example.runmateaibackend.global.config;


import com.example.runmateaibackend.global.jwt.JwtAuthenticationEntryPoint;
import com.example.runmateaibackend.global.jwt.JwtFilter;
import com.example.runmateaibackend.global.jwt.JwtUtil;
import com.example.runmateaibackend.global.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtUtil jwtUtil;
	private final CustomUserDetailsService userDetailsService;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	// 비밀번호 암호화 도구 (회원가입 시 비밀번호 해싱)
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// 인증 처리를 담당하는 매니저 (로그인 시 사용)
	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

		JwtFilter jwtFilter = new JwtFilter(jwtUtil, userDetailsService);

		http
			// CSRF 비활성화 (JWT는 세션을 사용 안 하므로 불필요)
			.csrf(csrf -> csrf.disable())

			// 세션을 사용하지 않음 (JWT는 무상태 방식)
			.sessionManagement(session ->
				session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

			// 인증 실패 시 처리할 핸들러 등록
			.exceptionHandling(exception ->
				exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))

			// API별 접근 권한 설정
			.authorizeHttpRequests(auth -> auth
				// 회원가입, 로그인은 인증 없이 접근 가능
				.requestMatchers("/api/auth/**").permitAll()
				// 그 외 모든 요청은 인증 필요
				.anyRequest().authenticated()
			)

			// JwtFilter를 UsernamePasswordAuthenticationFilter 앞에 배치
			// → 매 요청마다 JWT 토�큰 먼저 검사
			.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}

