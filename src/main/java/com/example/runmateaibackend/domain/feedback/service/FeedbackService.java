package com.example.runmateaibackend.domain.feedback.service;

import com.example.runmateaibackend.domain.feedback.dto.FeedbackResponse;
import com.example.runmateaibackend.domain.feedback.entity.AiFeedback;
import com.example.runmateaibackend.domain.feedback.repository.FeedbackRepository;
import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.record.repository.RecordRepository;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import com.example.runmateaibackend.global.client.ClaudeApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackService {

	private final UserRepository userRepository;
	private final RecordRepository recordRepository;
	private final PlanRepository planRepository;
	private final FeedbackRepository feedbackRepository;
	private final ClaudeApiClient claudeApiClient;
	private final FeedbackPromptBuilder feedbackPromptBuilder;

	@Transactional
	public FeedbackResponse createFeedback(String email, Long recordId) {

		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

		// 피드백 대상 기록 조회
		TrainingRecord targetRecord = recordRepository.findById(recordId)
			.orElseThrow(() -> new IllegalArgumentException("기록을 찾을 수 없습니다."));

		// 최근 기록 5개 조회 (추세 분석용)
		List<TrainingRecord> recentRecords = recordRepository.findTop5ByUserOrderByRunDateDesc(user);

		// 프롬프트 생성 후 Claude API 호출
		String prompt = feedbackPromptBuilder.build(targetRecord, recentRecords);
		String feedbackContent = claudeApiClient.sendMessage(prompt);

		// 현재 활성 플랜 조회 (피드백과 연결)
		TrainingPlan activePlan = planRepository.findByUserAndIsActive(user, true)
			.orElseThrow(() -> new IllegalArgumentException("활성화된 플랜이 없습니다."));

		AiFeedback feedback = AiFeedback.builder()
			.user(user)
			.trainingRecord(targetRecord)
			.trainingPlan(activePlan)
			.feedbackContent(feedbackContent)
			.planUpdated(false)
			.build();

		feedbackRepository.save(feedback);

		return new FeedbackResponse(feedback);
	}

	// 전체 피드백 조회
	public List<FeedbackResponse> getFeedbacks(String email) {

		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

		return feedbackRepository.findByUserOrderByCreatedAtDesc(user)
			.stream()
			.map(FeedbackResponse::new)
			.toList();
	}
}