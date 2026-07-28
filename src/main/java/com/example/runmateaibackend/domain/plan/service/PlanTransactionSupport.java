package com.example.runmateaibackend.domain.plan.service;

import java.time.LocalDate;

import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PlanTransactionSupport {

	private final PlanRepository planRepository;

	// 기존 활성 플랜이 있으면 비활성화한다. (짧은 트랜잭션, ms 단위로 끝남)
	@Transactional
	public void deactivateExistingActivePlan(User user) {
		planRepository.findFirstByUserAndIsActiveOrderByCreatedAtDesc(user, true)
			.ifPresent(existingPlan -> {
				existingPlan.deactivate();
				planRepository.save(existingPlan);
			});
	}

	// 새 플랜을 저장한다. (짧은 트랜잭션, ms 단위로 끝남)
	@Transactional
	public TrainingPlan saveNewPlan(User user, UserProfile profile, String planDataJson) {
		TrainingPlan newPlan = TrainingPlan.builder()
			.user(user)
			.planData(planDataJson)
			.goalType(profile.getGoalType())
			.startDate(LocalDate.now().plusDays(1))
			.isActive(true)
			.build();

		return planRepository.save(newPlan);
	}
}