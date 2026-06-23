package com.example.runmateaibackend.domain.feedback.service;

import com.example.runmateaibackend.domain.feedback.dto.AiFeedbackResult;
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
import tools.jackson.databind.ObjectMapper;
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
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Transactional
	public FeedbackResponse createFeedback(String email, Long recordId) {

		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

		TrainingRecord targetRecord = recordRepository.findById(recordId)
			.orElseThrow(() -> new IllegalArgumentException("기록을 찾을 수 없습니다."));

		List<TrainingRecord> recentRecords = recordRepository.findTop5ByUserOrderByRunDateDesc(user);

		TrainingPlan activePlan = planRepository.findByUserAndIsActive(user, true)
			.orElseThrow(() -> new IllegalArgumentException("활성화된 플랜이 없습니다."));

		// 프롬프트 생성 (현재 플랜 포함) 후 Claude API 호출 + JSON 파싱
		String prompt = feedbackPromptBuilder.build(targetRecord, recentRecords, activePlan);
		AiFeedbackResult result = claudeApiClient.sendMessageAndParse(prompt, AiFeedbackResult.class);

		TrainingPlan finalPlan = activePlan;

		// AI가 플랜 조정이 필요하다고 판단한 경우
		if (result.isPlanUpdateNeeded() && result.getUpdatedPlanData() != null) {

			// 기존 플랜 비활성화
			activePlan.deactivate();

			// 새 plan_data를 JSON 문자열로 변환
			String updatedPlanDataJson;
			try {
				updatedPlanDataJson = objectMapper.writeValueAsString(result.getUpdatedPlanData());
			} catch (Exception e) {
				throw new IllegalStateException("플랜 데이터 변환 실패: " + e.getMessage());
			}

			// 새 플랜 생성 (조정된 내용으로)
			TrainingPlan newPlan = TrainingPlan.builder()
				.user(user)
				.planData(updatedPlanDataJson)
				.goalType(activePlan.getGoalType())
				.isActive(true)
				.build();

			planRepository.save(newPlan);
			finalPlan = newPlan;
		}

		AiFeedback feedback = AiFeedback.builder()
			.user(user)
			.trainingRecord(targetRecord)
			.trainingPlan(finalPlan)
			.feedbackContent(result.getFeedback())
			.planUpdated(result.isPlanUpdateNeeded())
			.build();

		feedbackRepository.save(feedback);

		return new FeedbackResponse(feedback);
	}

	public List<FeedbackResponse> getFeedbacks(String email) {

		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

		return feedbackRepository.findByUserOrderByCreatedAtDesc(user)
			.stream()
			.map(FeedbackResponse::new)
			.toList();
	}
}