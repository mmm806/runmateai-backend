package com.example.runmateaibackend.domain.plan.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.runmateaibackend.domain.plan.dto.PlanDayProgressResponse;
import com.example.runmateaibackend.domain.plan.dto.PlanResponse;
import com.example.runmateaibackend.domain.plan.entity.PlanDayProgress;
import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanDayProgressRepository;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;
import com.example.runmateaibackend.domain.user.repository.UserProfileRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import com.example.runmateaibackend.global.client.ClaudeApiClient;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class PlanService {
	@PersistenceContext
	private EntityManager entityManager;

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final PlanRepository planRepository;
	private final PlanDayProgressRepository planDayProgressRepository;
	private final ClaudeApiClient claudeApiClient;
	private final PlanPromptBuilder planPromptBuilder;
	private final PlanDayLookup planDayLookup;

	// 새 플랜 생성
	@Transactional
	public PlanResponse createPlan(String email) {

		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

		UserProfile profile = userProfileRepository.findByUser(user)
			.orElseThrow(() -> new IllegalArgumentException("프로필을 먼저 등록해주세요."));

		// 기존 활성 플랜 비활성화
		planRepository.findByUserAndIsActive(user, true)
			.ifPresent(existingPlan -> {
				existingPlan.deactivate();
				planRepository.save(existingPlan);
			});

		entityManager.flush();

		// 프롬프트 생성 후 Claude API 호출
		String prompt = planPromptBuilder.build(profile);
		String planDataJson = claudeApiClient.sendMessage(prompt);

		// 새 플랜 저장 - 1주차 1일째는 항상 생성일의 "다음 날"로 고정
		TrainingPlan newPlan = TrainingPlan.builder()
			.user(user)
			.planData(planDataJson)
			.goalType(profile.getGoalType())
			.startDate(LocalDate.now().plusDays(1))
			.isActive(true)
			.build();

		planRepository.save(newPlan);

		return new PlanResponse(newPlan);
	}

	// 현재 활성 플랜 조회
	public PlanResponse getActivePlan(String email) {

		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

		TrainingPlan plan = planRepository.findByUserAndIsActive(user, true)
			.orElseThrow(() -> new IllegalArgumentException("활성화된 플랜이 없습니다."));

		List<PlanDayProgressResponse> progress = planDayProgressRepository.findByTrainingPlan(plan)
			.stream()
			.map(PlanDayProgressResponse::new)
			.toList();

		return new PlanResponse(plan, progress);
	}

	/**
	 * 러닝 기록이 생성/수정되었을 때 호출한다.
	 * 기록의 날짜(record.getRunDate())가 플랜상 어느 (주차, 일자)인지 찾아서,
	 * 그날 목표 거리와 실제 거리를 비교해 완료 여부를 자동으로 판정한다.
	 * - 목표 거리 이상으로 뛰었으면 완료(completed=true)
	 * - 목표 거리보다 적게 뛰었으면 미완료(completed=false)로 평가 결과를 남긴다.
	 * - 휴식일이거나 플랜 기간(시작일 이전)에 해당하지 않으면 아무 일도 하지 않는다.
	 */
	@Transactional
	public void evaluateCompletion(TrainingRecord record) {
		if (record.getTrainingPlan() == null) {
			return;
		}

		TrainingPlan plan = record.getTrainingPlan();
		Optional<int[]> weekAndDay = plan.resolveWeekAndDay(record.getRunDate());
		if (weekAndDay.isEmpty()) {
			return;
		}

		int week = weekAndDay.get()[0];
		int day = weekAndDay.get()[1];

		Optional<BigDecimal> plannedDistance = planDayLookup.findPlannedDistance(plan.getPlanData(), week, day);
		if (plannedDistance.isEmpty()) {
			// 휴식일이거나 플랜에 해당 날짜 데이터가 없으면 자동 완료 평가 대상이 아니다.
			return;
		}

		PlanDayProgress progress = planDayProgressRepository
			.findByTrainingPlanAndWeekNumberAndDayNumber(plan, week, day)
			.orElseGet(() -> planDayProgressRepository.save(
				PlanDayProgress.builder()
					.trainingPlan(plan)
					.weekNumber(week)
					.dayNumber(day)
					.build()
			));

		boolean achieved = record.getDistanceKm().compareTo(plannedDistance.get()) >= 0;
		if (achieved) {
			progress.markCompleted(record, LocalDateTime.now());
		} else {
			progress.markIncomplete(record);
		}
	}

	/**
	 * 기록이 삭제될 때 호출한다. 그 기록 때문에 평가되었던 진행 상태를 초기화한다.
	 * (단, 평가 자체를 지우는게 아니라 '미평가' 상태로 되돌린다 — 같은 날짜에
	 * 다른 기록이 남아있다면 호출 측에서 다시 evaluateCompletion을 호출해 재평가해야 한다.)
	 */
	@Transactional
	public void revertCompletionForRecord(TrainingRecord record) {
		planDayProgressRepository.findByTriggeringRecord(record)
			.forEach(PlanDayProgress::clear);
	}
}