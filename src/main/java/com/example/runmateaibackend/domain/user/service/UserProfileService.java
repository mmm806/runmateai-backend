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

	// 프로필 등록
	@Transactional
	public void createProfile(String email, ProfileRequest request) {

		User user = findUserByEmail(email);

		// 이미 프로필이 있으면 예외 처리
		if (userProfileRepository.findByUser(user).isPresent()) {
			throw new IllegalArgumentException("이미 프로필이 등록되어 있습니다.");
		}

		UserProfile profile = UserProfile.builder()
			.user(user)
			.currentPace(request.getCurrentPace())
			.weeklyRuns(request.getWeeklyRuns())
			.goalType(request.getGoalType())
			.targetWeeks(request.getTargetWeeks())
			.fitnessLevel(request.getFitnessLevel())
			.build();

		userProfileRepository.save(profile);
	}

	// 프로필 조회
	public ProfileResponse getProfile(String email) {

		User user = findUserByEmail(email);

		UserProfile profile = userProfileRepository.findByUser(user)
			.orElseThrow(() -> new IllegalArgumentException("등록된 프로필이 없습니다."));

		return new ProfileResponse(profile);
	}

	// 이메일로 유저 조회 (내부 공통 메서드)
	private User findUserByEmail(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
	}

	// 프로필 수정
	@Transactional
	public void updateProfile(String email, ProfileRequest request) {

		User user = findUserByEmail(email);

		UserProfile profile = userProfileRepository.findByUser(user)
			.orElseThrow(() -> new IllegalArgumentException("등록된 프로필이 없습니다."));

		profile.update(
			request.getCurrentPace(),
			request.getWeeklyRuns(),
			request.getGoalType(),
			request.getTargetWeeks(),
			request.getFitnessLevel()
		);
	}
}