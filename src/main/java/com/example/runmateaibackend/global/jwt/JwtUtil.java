package com.example.runmateaibackend.global.jwt;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

	private final SecretKey secretKey;
	private final long accessExpiration;
	private final long refreshExpiration;

	public JwtUtil(
		@Value("${jwt.secret}") String secret,
		@Value("${jwt.access-expiration}") long accessExpiration,
		@Value("${jwt.refresh-expiration}") long refreshExpiration
	) {
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessExpiration = accessExpiration;
		this.refreshExpiration = refreshExpiration;
	}

	// 액세스 토큰 생성 (짧은 유효기간)
	public String generateAccessToken(String email) {
		return generateToken(email, accessExpiration);
	}

	// 리프레시 토큰 생성 (긴 유효기간)
	public String generateRefreshToken(String email) {
		return generateToken(email, refreshExpiration);
	}

	// 토큰 생성 (내부 공통 메서드)
	private String generateToken(String email, long expiration) {
		return Jwts.builder()
			.subject(email)
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + expiration))
			.signWith(secretKey)
			.compact();
	}

	// 토큰에서 이메일 추출
	public String getEmailFromToken(String token) {
		return getClaims(token).getSubject();
	}

	// 토큰 유효성 검증
	public boolean validateToken(String token) {
		try {
			getClaims(token);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	// 리프레시 토큰 만료 시간 반환 (DB 저장 시 사용)
	public long getRefreshExpiration() {
		return refreshExpiration;
	}

	private Claims getClaims(String token) {
		return Jwts.parser()
			.verifyWith(secretKey)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}
}