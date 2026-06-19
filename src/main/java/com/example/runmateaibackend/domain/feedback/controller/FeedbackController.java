package com.example.runmateaibackend.domain.feedback.controller;

import com.example.runmateaibackend.domain.feedback.dto.FeedbackResponse;
import com.example.runmateaibackend.domain.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

	private final FeedbackService feedbackService;

	// 특정 기록에 대한 AI 피드백 생성
	@PostMapping("/{recordId}")
	public ResponseEntity<FeedbackResponse> createFeedback(
		Authentication authentication,
		@PathVariable Long recordId
	) {
		String email = authentication.getName();
		FeedbackResponse response = feedbackService.createFeedback(email, recordId);
		return ResponseEntity.ok(response);
	}

	// 전체 피드백 조회
	@GetMapping
	public ResponseEntity<List<FeedbackResponse>> getFeedbacks(Authentication authentication) {
		String email = authentication.getName();
		List<FeedbackResponse> responses = feedbackService.getFeedbacks(email);
		return ResponseEntity.ok(responses);
	}
}