package com.example.runmateaibackend.global.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

/**
 * JwtUtil 단위 테스트.
 *
 * Spring 컨텍스트 없이 생성자에 직접 값을 주입해서 테스트한다 (@Value로 주입되는
 * jwt.secret / expiration 값들은 application.yml 환경변수 의존이라, 컨텍스트를 띄우지
 * 않고도 실제 토큰 발급/검증 로직 자체를 검증하는 게 목적이다).
 *
 * 특히 "만료된 토큰"과 "다른 키로 서명된 토큰"은 실제로 시간이 지나기를 기다리거나
 * 외부 토큰을 구해올 필요 없이, 생성자에 넘기는 expiration/secret 값만 조작해서
 * 그 자리에서 바로 재현할 수 있다.
 */
class JwtUtilTest {

	// HS256은 최소 256비트(32바이트) 키를 요구하므로 충분히 긴 문자열을 사용한다.
	private static final String SECRET = "test-secret-key-please-make-it-long-enough-for-hmac-sha256-1234567890";
	private static final long ACCESS_EXPIRATION = 1_800_000L;   // 30분
	private static final long REFRESH_EXPIRATION = 1_209_600_000L; // 14일

	private JwtUtil jwtUtil;

	@BeforeEach
	void setUp() {
		jwtUtil = new JwtUtil(SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
	}

	@Test
	@DisplayName("액세스 토큰을 생성하면, 그 토큰에서 다시 같은 이메일을 추출할 수 있다")
	void generateAccessToken_and_getEmailFromToken_roundTrip() {
		String token = jwtUtil.generateAccessToken("user@runmateai.com");

		assertThat(jwtUtil.getEmailFromToken(token)).isEqualTo("user@runmateai.com");
	}

	@Test
	@DisplayName("리프레시 토큰을 생성하면, 그 토큰에서 다시 같은 이메일을 추출할 수 있다")
	void generateRefreshToken_and_getEmailFromToken_roundTrip() {
		String token = jwtUtil.generateRefreshToken("user2@runmateai.com");

		assertThat(jwtUtil.getEmailFromToken(token)).isEqualTo("user2@runmateai.com");
	}

	@Test
	@DisplayName("정상적으로 발급된 토큰은 유효성 검증을 통과한다")
	void validateToken_validToken_returnsTrue() {
		String token = jwtUtil.generateAccessToken("user@runmateai.com");

		assertThat(jwtUtil.validateToken(token)).isTrue();
	}

	@Test
	@DisplayName("만료 시간이 지난 토큰은 유효성 검증에 실패한다")
	void validateToken_expiredToken_returnsFalse() {
		// 만료 시간을 음수로 주면, 발급 즉시 "이미 만료된" 토큰이 만들어진다.
		JwtUtil utilWithAlreadyExpired = new JwtUtil(SECRET, -1_000L, -1_000L);
		String expiredToken = utilWithAlreadyExpired.generateAccessToken("user@runmateai.com");

		assertThat(jwtUtil.validateToken(expiredToken)).isFalse();
	}

	@Test
	@DisplayName("다른 시크릿 키로 서명된 토큰(변조된 토큰)은 유효성 검증에 실패한다")
	void validateToken_wrongSignature_returnsFalse() {
		JwtUtil utilWithDifferentSecret = new JwtUtil(
			"another-completely-different-secret-key-also-long-enough-987654321",
			ACCESS_EXPIRATION, REFRESH_EXPIRATION
		);
		String tokenSignedWithDifferentKey = utilWithDifferentSecret.generateAccessToken("attacker@runmateai.com");

		assertThat(jwtUtil.validateToken(tokenSignedWithDifferentKey)).isFalse();
	}

	@Test
	@DisplayName("JWT 형식 자체가 아닌 문자열은 유효성 검증에 실패한다")
	void validateToken_malformedString_returnsFalse() {
		assertThat(jwtUtil.validateToken("this-is-not-a-jwt-token")).isFalse();
	}

	@Test
	@DisplayName("만료된 토큰에서 이메일 추출을 시도하면 예외가 발생한다")
	void getEmailFromToken_expiredToken_throwsException() {
		JwtUtil utilWithAlreadyExpired = new JwtUtil(SECRET, -1_000L, -1_000L);
		String expiredToken = utilWithAlreadyExpired.generateAccessToken("user@runmateai.com");

		assertThatThrownBy(() -> jwtUtil.getEmailFromToken(expiredToken))
			.isInstanceOf(JwtException.class);
	}

	@Test
	@DisplayName("리프레시 토큰 만료 시간을 생성자에 넘긴 값 그대로 반환한다 (DB 저장용)")
	void getRefreshExpiration_returnsConfiguredValue() {
		assertThat(jwtUtil.getRefreshExpiration()).isEqualTo(REFRESH_EXPIRATION);
	}
}