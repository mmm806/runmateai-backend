package com.example.runmateaibackend.global.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.runmateaibackend.domain.user.entity.Role;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.global.security.CustomUserDetails;

import jakarta.servlet.FilterChain;

/**
 * JwtFilter 단위 테스트.
 *
 * doFilterInternal()은 protected 메서드라 OncePerRequestFilter의 public doFilter()를
 * 거치면(비동기 디스패치 판별 등 부가 로직이 섞여 들어옴) 테스트가 불필요하게 복잡해진다.
 * ReflectionTestUtils.invokeMethod로 protected 메서드를 직접 호출해서, 이 필터가
 * 실제로 하는 일(토큰 파싱 → 검증 → SecurityContext 설정)만 순수하게 검증한다.
 *
 * SecurityContextHolder는 스레드 로컬 상태를 전역으로 공유하므로, 각 테스트 전후로
 * 반드시 비워서 테스트 간 오염을 막는다.
 */
@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

	@Mock
	private JwtUtil jwtUtil;
	@Mock
	private UserDetailsService userDetailsService;
	@Mock
	private FilterChain filterChain;

	private JwtFilter jwtFilter;

	@BeforeEach
	void setUp() {
		jwtFilter = new JwtFilter(jwtUtil, userDetailsService);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private void invokeFilter(MockHttpServletRequest request, MockHttpServletResponse response) {
		ReflectionTestUtils.invokeMethod(jwtFilter, "doFilterInternal", request, response, filterChain);
	}

	@Test
	@DisplayName("Authorization 헤더가 없으면 인증 처리 없이 다음 필터로 넘어간다")
	void doFilterInternal_noAuthorizationHeader_passesThroughWithoutAuthentication() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		invokeFilter(request, response);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	@DisplayName("Authorization 헤더가 'Bearer '로 시작하지 않으면 토큰이 없는 것으로 취급한다")
	void doFilterInternal_headerNotBearerFormat_passesThroughWithoutAuthentication() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
		MockHttpServletResponse response = new MockHttpServletResponse();

		invokeFilter(request, response);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	@DisplayName("유효하지 않은 토큰이면 SecurityContext에 인증 정보를 설정하지 않는다")
	void doFilterInternal_invalidToken_doesNotAuthenticate() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer invalid-token");
		MockHttpServletResponse response = new MockHttpServletResponse();

		when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

		invokeFilter(request, response);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(userDetailsService, never()).loadUserByUsername(org.mockito.ArgumentMatchers.any());
		verify(filterChain).doFilter(request, response);
	}

	@Test
	@DisplayName("유효한 토큰이고 계정이 잠기지 않았으면 SecurityContext에 인증 정보를 설정한다")
	void doFilterInternal_validTokenAndUnlockedAccount_setsAuthentication() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer valid-token");
		MockHttpServletResponse response = new MockHttpServletResponse();

		User user = User.builder()
			.id(1L).email("user@runmateai.com").password("encoded").name("테스트유저")
			.role(Role.USER).locked(false).build();
		CustomUserDetails userDetails = new CustomUserDetails(user);

		when(jwtUtil.validateToken("valid-token")).thenReturn(true);
		when(jwtUtil.getEmailFromToken("valid-token")).thenReturn("user@runmateai.com");
		when(userDetailsService.loadUserByUsername("user@runmateai.com")).thenReturn(userDetails);

		invokeFilter(request, response);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userDetails);
		assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
			.extracting(Object::toString).containsExactly("ROLE_USER");
		verify(filterChain).doFilter(request, response);
	}

	@Test
	@DisplayName("유효한 토큰이어도 관리자가 잠근 계정이면 인증 정보를 설정하지 않고 즉시 다음 필터로 넘어간다")
	void doFilterInternal_validTokenButLockedAccount_doesNotAuthenticate() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer valid-token");
		MockHttpServletResponse response = new MockHttpServletResponse();

		User lockedUser = User.builder()
			.id(2L).email("locked@runmateai.com").password("encoded").name("잠긴유저")
			.role(Role.USER).locked(true).build();
		CustomUserDetails userDetails = new CustomUserDetails(lockedUser);

		when(jwtUtil.validateToken("valid-token")).thenReturn(true);
		when(jwtUtil.getEmailFromToken("valid-token")).thenReturn("locked@runmateai.com");
		when(userDetailsService.loadUserByUsername("locked@runmateai.com")).thenReturn(userDetails);

		invokeFilter(request, response);

		// 인증 객체를 세팅하지 않아, 이후 인가 단계에서 미인증 요청으로 처리되어 401로 이어진다.
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}
}