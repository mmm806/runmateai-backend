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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackService {

	@PersistenceContext
	private EntityManager entityManager;
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

		// 이미 이 기록에 대한 피드백이 있으면 차단 (중복 요청 방지)
		if (!feedbackRepository.findByTrainingRecordId(recordId).isEmpty()) {
			throw new IllegalArgumentException("이미 이 기록에 대한 피드백이 존재합니다.");
		}

		List<TrainingRecord> recentRecords = recordRepository.findTop5ByUserOrderByRunDateDesc(user);

		TrainingPlan activePlan = planRepository.findByUserAndIsActive(user, true)
			.orElseThrow(() -> new IllegalArgumentException("활성화된 플랜이 없습니다."));

		String prompt = feedbackPromptBuilder.build(targetRecord, recentRecords, activePlan);
		AiFeedbackResult result = claudeApiClient.sendMessageAndParse(prompt, AiFeedbackResult.class);

		TrainingPlan finalPlan = activePlan;

		if (result.isPlanUpdateNeeded() && result.getUpdatedPlanData() != null) {

			activePlan.deactivate();
			planRepository.save(activePlan);
			entityManager.flush();  // ← 즉시 DB에 UPDATE 반영시키기

			// 그 다음에 새 플랜 생성
			String updatedPlanDataJson;
			try {
				updatedPlanDataJson = objectMapper.writeValueAsString(result.getUpdatedPlanData());
			} catch (Exception e) {
				throw new IllegalStateException("플랜 데이터 변환 실패: " + e.getMessage());
			}

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