package com.example.runmateaibackend.domain.user.service;

import com.example.runmateaibackend.domain.user.dto.ProfileRequest;
import com.example.runmateaibackend.domain.user.dto.ProfileResponse;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;
import com.example.runmateaibackend.domain.user.repository.UserProfileRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;

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