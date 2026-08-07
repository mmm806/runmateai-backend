package com.example.runmateaibackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.runmateaibackend.domain.feedback.repository.FeedbackRepository;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.record.repository.RecordRepository;
import com.example.runmateaibackend.domain.user.dto.LoginRequest;
import com.example.runmateaibackend.domain.user.dto.SignupRequest;
import com.example.runmateaibackend.domain.user.dto.TokenResponse;
import com.example.runmateaibackend.domain.user.entity.Role;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.repository.RefreshTokenRepository;
import com.example.runmateaibackend.domain.user.repository.UserProfileRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import com.example.runmateaibackend.global.exception.ConflictException;
import com.example.runmateaibackend.global.exception.ForbiddenException;
import com.example.runmateaibackend.global.exception.UnauthorizedException;
import com.example.runmateaibackend.global.jwt.JwtUtil;

/**
 * AuthService 단위 테스트.
 *
 * 실제 DB, 실제 JWT 서명 없이 Mockito로 의존성을 대체해서 로그인/회원가입의
 * 핵심 규칙(비밀번호 검증, 계정 잠금, 중복 가입 방지)을 검증한다.
 * DTO(LoginRequest, SignupRequest)는 setter가 없는 순수 요청 객체라
 * ReflectionTestUtils로 필드를 직접 채워 넣는다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private FeedbackRepository feedbackRepository;
	@Mock
	private RecordRepository recordRepository;
	@Mock
	private PlanRepository planRepository;
	@Mock
	private UserProfileRepository userProfileRepository;
	@Mock
	private RefreshTokenRepository refreshTokenRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private JwtUtil jwtUtil;

	private AuthService authService;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		authService = new AuthService(
			userRepository, feedbackRepository, recordRepository, planRepository,
			userProfileRepository, refreshTokenRepository, passwordEncoder, jwtUtil
		);
	}

	private LoginRequest loginRequest(String email, String password) {
		LoginRequest request = new LoginRequest();
		ReflectionTestUtils.setField(request, "email", email);
		ReflectionTestUtils.setField(request, "password", password);
		return request;
	}

	private SignupRequest signupRequest(String email, String password, String name) {
		SignupRequest request = new SignupRequest();
		ReflectionTestUtils.setField(request, "email", email);
		ReflectionTestUtils.setField(request, "password", password);
		ReflectionTestUtils.setField(request, "name", name);
		return request;
	}

	private User buildUser(String email, String encodedPassword, boolean locked) {
		return User.builder()
			.id(1L)
			.email(email)
			.password(encodedPassword)
			.name("테스트유저")
			.role(Role.USER)
			.locked(locked)
			.build();
	}

	@Test
	@DisplayName("이미 가입된 이메일로 회원가입 시도 시 ConflictException이 발생한다")
	void signup_duplicateEmail_throwsConflictException() {
		when(userRepository.existsByEmail("dup@runmateai.com")).thenReturn(true);

		SignupRequest request = signupRequest("dup@runmateai.com", "password1234", "홍길동");

		assertThatThrownBy(() -> authService.signup(request))
			.isInstanceOf(ConflictException.class)
			.hasMessage("이미 사용 중인 이메일입니다.");

		// 이메일 중복 시점에 이미 거부되어야 하므로, 비밀번호 암호화나 저장까지 가면 안 된다.
		verify(passwordEncoder, never()).encode(anyString());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("정상적인 회원가입 시 비밀번호가 암호화되어 저장된다")
	void signup_success_encodesPasswordBeforeSaving() {
		when(userRepository.existsByEmail("new@runmateai.com")).thenReturn(false);
		when(passwordEncoder.encode("password1234")).thenReturn("encoded-password-hash");

		SignupRequest request = signupRequest("new@runmateai.com", "password1234", "홍길동");

		authService.signup(request);

		verify(userRepository).save(argThatUserHasEncodedPassword());
	}

	private User argThatUserHasEncodedPassword() {
		return org.mockito.ArgumentMatchers.argThat(user ->
			user.getEmail().equals("new@runmateai.com")
				&& user.getPassword().equals("encoded-password-hash")
				&& user.getName().equals("홍길동")
		);
	}

	@Test
	@DisplayName("존재하지 않는 이메일로 로그인하면 401 Unauthorized (이메일 존재 여부는 노출하지 않는다)")
	void login_emailNotFound_throwsUnauthorizedException() {
		when(userRepository.findByEmail("nobody@runmateai.com")).thenReturn(Optional.empty());

		LoginRequest request = loginRequest("nobody@runmateai.com", "anyPassword");

		assertThatThrownBy(() -> authService.login(request))
			.isInstanceOf(UnauthorizedException.class)
			.hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
	}

	@Test
	@DisplayName("비밀번호가 틀리면 401 Unauthorized가 발생한다")
	void login_wrongPassword_throwsUnauthorizedException() {
		User user = buildUser("user@runmateai.com", "encoded-hash", false);
		when(userRepository.findByEmail("user@runmateai.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrongPassword", "encoded-hash")).thenReturn(false);

		LoginRequest request = loginRequest("user@runmateai.com", "wrongPassword");

		assertThatThrownBy(() -> authService.login(request))
			.isInstanceOf(UnauthorizedException.class)
			.hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
	}

	@Test
	@DisplayName("비밀번호는 맞지만 잠긴 계정이면 403 Forbidden이 발생한다")
	void login_lockedAccount_throwsForbiddenException() {
		User lockedUser = buildUser("locked@runmateai.com", "encoded-hash", true);
		when(userRepository.findByEmail("locked@runmateai.com")).thenReturn(Optional.of(lockedUser));
		when(passwordEncoder.matches("correctPassword", "encoded-hash")).thenReturn(true);

		LoginRequest request = loginRequest("locked@runmateai.com", "correctPassword");

		assertThatThrownBy(() -> authService.login(request))
			.isInstanceOf(ForbiddenException.class)
			.hasMessage("잠긴 계정입니다. 관리자에게 문의해주세요.");

		// 잠금 상태 확인 전에 토큰이 발급되면 안 된다.
		verify(jwtUtil, never()).generateAccessToken(anyString());
	}

	@Test
	@DisplayName("잠긴 계정이라도 비밀번호가 틀리면 잠김 여부보다 자격 증명 오류가 먼저 보고된다")
	void login_lockedAccountWithWrongPassword_stillReportsUnauthorizedFirst() {
		User lockedUser = buildUser("locked2@runmateai.com", "encoded-hash", true);
		when(userRepository.findByEmail("locked2@runmateai.com")).thenReturn(Optional.of(lockedUser));
		when(passwordEncoder.matches("wrongPassword", "encoded-hash")).thenReturn(false);

		LoginRequest request = loginRequest("locked2@runmateai.com", "wrongPassword");

		// 계정이 잠겨 있어도, 비밀번호 검증이 먼저이므로 401이 나와야 한다
		// (공격자가 응답 코드만으로 "이 계정이 잠겼는지"를 추론하지 못하게 하는 순서).
		assertThatThrownBy(() -> authService.login(request))
			.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	@DisplayName("정상 로그인 시 액세스/리프레시 토큰을 발급하고 리프레시 토큰을 저장한다")
	void login_success_issuesTokensAndSavesRefreshToken() {
		User user = buildUser("ok@runmateai.com", "encoded-hash", false);
		when(userRepository.findByEmail("ok@runmateai.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("correctPassword", "encoded-hash")).thenReturn(true);
		when(jwtUtil.generateAccessToken("ok@runmateai.com")).thenReturn("access-token-value");
		when(jwtUtil.generateRefreshToken("ok@runmateai.com")).thenReturn("refresh-token-value");
		when(jwtUtil.getRefreshExpiration()).thenReturn(1_209_600_000L);
		when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());

		LoginRequest request = loginRequest("ok@runmateai.com", "correctPassword");

		TokenResponse response = authService.login(request);

		assertThat(response.getAccessToken()).isEqualTo("access-token-value");
		assertThat(response.getRefreshToken()).isEqualTo("refresh-token-value");
		verify(refreshTokenRepository, times(1)).save(any());
	}
}