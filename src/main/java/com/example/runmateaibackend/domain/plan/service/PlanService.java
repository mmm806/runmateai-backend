package com.example.runmateaibackend.domain.plan.service;

import com.example.runmateaibackend.domain.plan.dto.PlanResponse;
import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;
import com.example.runmateaibackend.domain.user.repository.UserProfileRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import com.example.runmateaibackend.global.client.ClaudeApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final PlanRepository planRepository;
	private final ClaudeApiClient claudeApiClient;
	private final PlanPromptBuilder planPromptBuilder;

	// 새 플랜 생성
	@Transactional
	public PlanResponse createPlan(String email) {

		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

		UserProfile profile = userProfileRepository.findByUser(user)
			.orElseThrow(() -> new IllegalArgumentException("프로필을 먼저 등록해주세요."));

		// 기존 활성 플랜 비활성화
		planRepository.findByUserAndIsActive(user, true)
			.ifPresent(TrainingPlan::deactivate);

		// 프롬프트 생성 후 Claude API 호출
		String prompt = planPromptBuilder.build(profile);
		String planDataJson = claudeApiClient.sendMessage(prompt);

		// 새 플랜 저장
		TrainingPlan newPlan = TrainingPlan.builder()
			.user(user)
			.planData(planDataJson)
			.goalType(profile.getGoalType())
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

		return new PlanResponse(plan);
	}
}