package com.example.runmateaibackend.domain.user.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.runmateaibackend.domain.user.dto.LoginRequest;
import com.example.runmateaibackend.domain.user.dto.SignupRequest;
import com.example.runmateaibackend.domain.user.dto.TokenResponse;
import com.example.runmateaibackend.domain.user.entity.RefreshToken;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.repository.RefreshTokenRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import com.example.runmateaibackend.global.jwt.JwtUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtUtil jwtUtil;

	// 회원가입
	@Transactional
	public void signup(SignupRequest request) {
		// 이메일 중복 확인
		if (userRepository.existsByEmail(request.getEmail())) {
			throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
		}

		// 비밀번호 암호화
		String encodedPassword = passwordEncoder.encode(request.getPassword());

		// User 생성 및 저장
		User user = User.builder()
			.email(request.getEmail())
			.password(encodedPassword)
			.name(request.getName())
			.build();

		userRepository.save(user);
	}

	// 로그인
	@Transactional
	public TokenResponse login(LoginRequest request) {

		// 이메일로 유저 조회
		User user = userRepository.findByEmail(request.getEmail())
			.orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

		// 비밀번호 일치 확인
		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
		}

		// 액세스 토큰, 리프레시 토큰 생성
		String accessToken = jwtUtil.generateAccessToken(user.getEmail());
		String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

		// 리프레시 토큰 저장 (이미 있으면 갱신, 업으면 새로 생성)
		LocalDateTime expiresAt = LocalDateTime.now()
			.plusSeconds(jwtUtil.getRefreshExpiration() / 1000);

		refreshTokenRepository.findByUser(user)
			.ifPresentOrElse(
				existing -> existing.updateToken(refreshToken, expiresAt),
				() -> refreshTokenRepository.save(
					RefreshToken.builder()
						.user(user)
						.token(refreshToken)
						.expiresAt(expiresAt)
						.build()
				)
			);
		return new TokenResponse(accessToken, refreshToken);

	}

	// 토큰 재발급
	@Transactional
	public TokenResponse reissue(String refreshToken) {

		//토큰 자체 유효성 검증 (만료, 위조 여부)
		if (!jwtUtil.validateToken(refreshToken)) {
			throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.");
		}

		// DB에서 토큰 조회
		RefreshToken savedToken = refreshTokenRepository.findByToken(refreshToken)
			.orElseThrow(() -> new IllegalArgumentException("저장된 리프레시 토큰이 없습니다."));

		// DB 기준 만료 여부 확인
		if (savedToken.isExpired()) {
			throw new IllegalArgumentException("만료된 리프레시 토큰입니다.");
		}

		String email = jwtUtil.getEmailFromToken(refreshToken);

		// 새 토큰 발급
		String newAccessToken = jwtUtil.generateAccessToken(email);
		String newRefreshToken = jwtUtil.generateRefreshToken(email);

		LocalDateTime newExpiresAt = LocalDateTime.now()
			.plusSeconds(jwtUtil.getRefreshExpiration() / 1000);

		savedToken.updateToken(newRefreshToken, newExpiresAt);

		return new TokenResponse(newAccessToken, newRefreshToken);
	}
}
