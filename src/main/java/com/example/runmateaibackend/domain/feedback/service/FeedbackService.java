package com.example.runmateaibackend.domain.feedback.service;

import com.example.runmateaibackend.domain.feedback.dto.AiFeedbackResult;
import com.example.runmateaibackend.domain.feedback.dto.FeedbackResponse;
import com.example.runmateaibackend.domain.feedback.entity.AiFeedback;
import com.example.runmateaibackend.domain.feedback.repository.FeedbackRepository;
import com.example.runmateaibackend.domain.plan.entity.PlanDayProgress;
import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanDayProgressRepository;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.plan.service.PlanDataMerger;
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

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackService {

	@PersistenceContext
	private EntityManager entityManager;
	private final UserRepository userRepository;
	private final RecordRepository recordRepository;
	private final PlanRepository planRepository;
	private final PlanDayProgressRepository planDayProgressRepository;
	private final PlanDataMerger planDataMerger;
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

			// AI가 과거 일정까지 다시 써버렸을 가능성에 대비해, 코드 레벨에서 강제로
			// "오늘 이전 = 옛 플랜 그대로, 오늘 이후 = AI의 새 플랜"으로 병합한다.
			String mergedPlanDataJson = planDataMerger.merge(
				activePlan.getPlanData(),
				updatedPlanDataJson,
				activePlan.getStartDate(),
				LocalDate.now()
			);

			TrainingPlan newPlan = TrainingPlan.builder()
				.user(user)
				.planData(mergedPlanDataJson)
				.goalType(activePlan.getGoalType())
				.startDate(activePlan.getStartDate()) // 갱신된 플랜도 기존 플랜과 동일한 날짜 좌표계를 유지한다
				.isActive(true)
				.build();

			planRepository.save(newPlan);

			// 옛 플랜에 쌓여있던 완료 기록(PlanDayProgress)을 새 플랜으로 이관한다.
			// 그렇지 않으면 plan_id가 바뀌면서 완료율이 0%로 초기화되어 보인다.
			// AI는 "앞으로 남은 일정"만 다시 짜고 지나간 일정은 그대로 두므로,
			// 같은 (week, day) 좌표의 완료 기록은 새 플랜에서도 그대로 유효하다.
			carryOverProgress(activePlan, newPlan);

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

	/**
	 * 옛 플랜(oldPlan)에 쌓여있던 (week, day)별 완료 기록을 새 플랜(newPlan)으로 복사한다.
	 * 기존 row를 옮기는(plan_id를 갱신하는) 대신, 새 row를 만들어 새 플랜에 연결한다 —
	 * 옛 플랜의 진행 기록도 그 자체로 과거 이력으로 남겨두기 위함이다.
	 */
	private void carryOverProgress(TrainingPlan oldPlan, TrainingPlan newPlan) {

		List<PlanDayProgress> oldProgressList = planDayProgressRepository.findByTrainingPlan(oldPlan);

		List<PlanDayProgress> carriedOver = oldProgressList.stream()
			.map(old -> PlanDayProgress.builder()
				.trainingPlan(newPlan)
				.weekNumber(old.getWeekNumber())
				.dayNumber(old.getDayNumber())
				.completed(old.isCompleted())
				.triggeringRecord(old.getTriggeringRecord())
				.completedAt(old.getCompletedAt())
				.build())
			.toList();

		planDayProgressRepository.saveAll(carriedOver);
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