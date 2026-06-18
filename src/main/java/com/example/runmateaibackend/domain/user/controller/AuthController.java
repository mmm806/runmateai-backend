package com.example.runmateaibackend.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.runmateaibackend.domain.user.dto.LoginRequest;
import com.example.runmateaibackend.domain.user.dto.SignupRequest;
import com.example.runmateaibackend.domain.user.dto.TokenResponse;
import com.example.runmateaibackend.domain.user.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	// 회원가입
	@PostMapping("/signup")
	public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
		authService.signup(request);
		return ResponseEntity.ok("회원가입이 완료되었습니다");
	}

	// 로그인
	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		TokenResponse tokenResponse = authService.login(request);
		return ResponseEntity.ok(tokenResponse);
	}

	// 토큰 재발급
	@PostMapping("/reissue")
	public ResponseEntity<TokenResponse> reissue(@RequestBody String refreshToken) {
		TokenResponse tokenResponse = authService.reissue(refreshToken);
		return ResponseEntity.ok(tokenResponse);
	}
}
