package com.example.runmateaibackend.domain.user.service;

import com.example.runmateaibackend.domain.plan.service.PlanService;
import com.example.runmateaibackend.domain.user.dto.ProfileRequest;
import com.example.runmateaibackend.domain.user.dto.ProfileResponse;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;
import com.example.runmateaibackend.domain.user.repository.UserProfileRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

	@PersistenceContext
	private EntityManager entityManager;

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final PlanService planService;

	@Transactional
	public void createProfile(String email, ProfileRequest request) {

		User user = findUserByEmail(email);

		if (userProfileRepository.findByUser(user).isPresent()) {
			throw new IllegalArgumentException("이미 프로필이 등록되어 있습니다.");
		}

		UserProfile profile = UserProfile.builder()
			.user(user)
			.targetPace(request.getTargetPace())
			.targetWeeklyRuns(request.getTargetWeeklyRuns())
			.goalType(request.getGoalType())
			.targetWeeks(request.getTargetWeeks())
			.fitnessLevel(request.getFitnessLevel())
			.monthlyGoalKm(request.getMonthlyGoalKm())
			.build();

		userProfileRepository.save(profile);

		// 프로필 등록이 끝나면 그 정보를 기반으로 AI 훈련 플랜을 바로 생성해준다.
		// PlanService.createPlan()이 같은 email로 유저/프로필을 다시 조회하므로
		// 방금 저장한 profile이 먼저 보이도록 flush 해둔다.
		entityManager.flush();
		planService.createPlan(email);
	}

	public ProfileResponse getProfile(String email) {

		User user = findUserByEmail(email);

		UserProfile profile = userProfileRepository.findByUser(user)
			.orElseThrow(() -> new IllegalArgumentException("등록된 프로필이 없습니다."));

		return new ProfileResponse(profile);
	}

	@Transactional
	public void updateProfile(String email, ProfileRequest request) {

		User user = findUserByEmail(email);

		UserProfile profile = userProfileRepository.findByUser(user)
			.orElseThrow(() -> new IllegalArgumentException("등록된 프로필이 없습니다."));

		profile.update(
			request.getTargetPace(),
			request.getTargetWeeklyRuns(),
			request.getGoalType(),
			request.getTargetWeeks(),
			request.getFitnessLevel(),
			request.getMonthlyGoalKm()
		);
	}

	private User findUserByEmail(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
	}
}