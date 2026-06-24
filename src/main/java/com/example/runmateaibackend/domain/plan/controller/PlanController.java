package com.example.runmateaibackend.domain.plan.controller;

import com.example.runmateaibackend.domain.plan.dto.PlanResponse;
import com.example.runmateaibackend.domain.plan.service.PlanService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

	private final PlanService planService;

	// 새 플랜 생성
	@PostMapping
	public ResponseEntity<PlanResponse> createPlan(Authentication authentication) {
		String email = authentication.getName();
		PlanResponse response = planService.createPlan(email);
		return ResponseEntity.ok(response);
	}

	// 현재 활성 플랜 조회
	@GetMapping("/active")
	public ResponseEntity<PlanResponse> getActivePlan(Authentication authentication) {
		String email = authentication.getName();
		PlanResponse response = planService.getActivePlan(email);
		return ResponseEntity.ok(response);
	}
}