package com.example.runmateaibackend.domain.user.controller;

import com.example.runmateaibackend.domain.user.dto.ProfileRequest;
import com.example.runmateaibackend.domain.user.dto.ProfileResponse;
import com.example.runmateaibackend.domain.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserProfileController {

	private final UserProfileService userProfileService;

	// 프로필 등록
	@PostMapping
	public ResponseEntity<String> createProfile(
		Authentication authentication,
		@Valid @RequestBody ProfileRequest request
	) {
		String email = authentication.getName(); // JwtFilter가 저장한 인증 정보에서 이메일 추출
		userProfileService.createProfile(email, request);
		return ResponseEntity.ok("프로필이 등록되었습니다.");
	}

	// 프로필 조회
	@GetMapping
	public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
		String email = authentication.getName();
		ProfileResponse response = userProfileService.getProfile(email);
		return ResponseEntity.ok(response);
	}
}