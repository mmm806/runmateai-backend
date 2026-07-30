package com.example.runmateaibackend.domain.admin.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.runmateaibackend.domain.admin.dto.AdminUserResponse;
import com.example.runmateaibackend.domain.admin.service.AdminService;

import lombok.RequiredArgsConstructor;

// 이 컨트롤러의 모든 API는 SecurityConfig에서 ROLE_ADMIN에게만 열려있다.
// (/api/admin/** 경로 전체가 hasRole("ADMIN")으로 제한됨)
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminController {

	private final AdminService adminService;

	// 전체 유저 목록 조회
	@GetMapping
	public ResponseEntity<List<AdminUserResponse>> getAllUsers() {
		return ResponseEntity.ok(adminService.getAllUsers());
	}

	// 계정 잠금
	@PatchMapping("/{userId}/lock")
	public ResponseEntity<Void> lockUser(@PathVariable Long userId) {
		adminService.lockUser(userId);
		return ResponseEntity.noContent().build();
	}

	// 계정 잠금 해제
	@PatchMapping("/{userId}/unlock")
	public ResponseEntity<Void> unlockUser(@PathVariable Long userId) {
		adminService.unlockUser(userId);
		return ResponseEntity.noContent().build();
	}

	// 계정 강제 삭제
	@DeleteMapping("/{userId}")
	public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
		adminService.deleteUser(userId);
		return ResponseEntity.noContent().build();
	}
}