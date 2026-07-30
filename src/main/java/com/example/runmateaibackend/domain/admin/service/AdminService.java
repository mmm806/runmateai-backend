package com.example.runmateaibackend.domain.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.runmateaibackend.domain.admin.dto.AdminUserResponse;
import com.example.runmateaibackend.domain.feedback.repository.FeedbackRepository;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.record.repository.RecordRepository;
import com.example.runmateaibackend.domain.user.entity.Role;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.repository.RefreshTokenRepository;
import com.example.runmateaibackend.domain.user.repository.UserProfileRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import com.example.runmateaibackend.global.exception.ConflictException;
import com.example.runmateaibackend.global.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final FeedbackRepository feedbackRepository;
	private final RecordRepository recordRepository;
	private final PlanRepository planRepository;
	private final RefreshTokenRepository refreshTokenRepository;

	// 전체 유저 목록 조회
	public List<AdminUserResponse> getAllUsers() {
		return userRepository.findAll().stream()
			.map(AdminUserResponse::new)
			.toList();
	}

	// 계정 잠금
	// 잠긴 계정은 이후 로그인 시도가 차단되고(AuthService.login),
	// 이미 발급된 토큰으로 요청이 와도 매 요청마다 JwtFilter에서 재확인되어 거부된다.
	@Transactional
	public void lockUser(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("유저를 찾을 수 없습니다."));

		if (user.getRole() == Role.ADMIN) {
			throw new ConflictException("관리자 계정은 잠글 수 없습니다.");
		}

		user.lock();
	}

	// 계정 잠금 해제
	@Transactional
	public void unlockUser(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("유저를 찾을 수 없습니다."));

		user.unlock();
	}

	// 계정 강제 삭제 (관리자용)
	// AuthService.withdraw()와 동일한 순서로 자식 테이블부터 정리한다.
	@Transactional
	public void deleteUser(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("유저를 찾을 수 없습니다."));

		if (user.getRole() == Role.ADMIN) {
			throw new ConflictException("관리자 계정은 삭제할 수 없습니다.");
		}

		feedbackRepository.deleteByUser(user);
		recordRepository.deleteByUser(user);
		planRepository.deleteByUser(user);
		userProfileRepository.findByUser(user).ifPresent(userProfileRepository::delete);
		refreshTokenRepository.deleteByUser(user);
		userRepository.delete(user);
	}
}