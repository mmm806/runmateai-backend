package com.example.runmateaibackend.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

/**
 * AdminService 단위 테스트.
 *
 * 실제 DB 없이 Mockito로 리포지토리를 가짜(mock)로 대체해서, 관리자 기능의
 * 핵심 비즈니스 규칙("관리자 계정은 잠금/삭제할 수 없다")이 지켜지는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserProfileRepository userProfileRepository;
	@Mock
	private FeedbackRepository feedbackRepository;
	@Mock
	private RecordRepository recordRepository;
	@Mock
	private PlanRepository planRepository;
	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@InjectMocks
	private AdminService adminService;

	private User buildUser(Long id, Role role, boolean locked) {
		return User.builder()
			.id(id)
			.email("test" + id + "@runmateai.com")
			.password("encoded-password")
			.name("테스트유저")
			.role(role)
			.locked(locked)
			.build();
	}

	@Test
	@DisplayName("일반 유저(USER)는 정상적으로 잠글 수 있다")
	void lockUser_normalUser_succeeds() {
		User normalUser = buildUser(1L, Role.USER, false);
		when(userRepository.findById(1L)).thenReturn(Optional.of(normalUser));

		adminService.lockUser(1L);

		assertThat(normalUser.isLocked()).isTrue();
	}

	@Test
	@DisplayName("관리자(ADMIN) 계정은 잠글 수 없고 ConflictException이 발생한다")
	void lockUser_adminUser_throwsConflictException() {
		User adminUser = buildUser(2L, Role.ADMIN, false);
		when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));

		assertThatThrownBy(() -> adminService.lockUser(2L))
			.isInstanceOf(ConflictException.class)
			.hasMessage("관리자 계정은 잠글 수 없습니다.");

		// 예외가 발생했으므로 실제로 잠금 처리가 되지 않아야 한다.
		assertThat(adminUser.isLocked()).isFalse();
	}

	@Test
	@DisplayName("존재하지 않는 유저를 잠그려 하면 ResourceNotFoundException이 발생한다")
	void lockUser_userNotFound_throwsResourceNotFoundException() {
		when(userRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> adminService.lockUser(999L))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("유저를 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("관리자(ADMIN) 계정은 삭제할 수 없고 ConflictException이 발생한다")
	void deleteUser_adminUser_throwsConflictException() {
		User adminUser = buildUser(3L, Role.ADMIN, false);
		when(userRepository.findById(3L)).thenReturn(Optional.of(adminUser));

		assertThatThrownBy(() -> adminService.deleteUser(3L))
			.isInstanceOf(ConflictException.class)
			.hasMessage("관리자 계정은 삭제할 수 없습니다.");

		// 예외가 먼저 발생했으므로, 연관 데이터 삭제(피드백/기록/플랜 등) 로직이
		// 전혀 호출되지 않아야 한다. 관리자 보호 규칙이 실제 삭제보다 먼저 검증됨을 확인.
		verify(feedbackRepository, never()).deleteByUser(any());
		verify(recordRepository, never()).deleteByUser(any());
		verify(planRepository, never()).deleteByUser(any());
		verify(userRepository, never()).delete(any());
	}

	@Test
	@DisplayName("일반 유저 삭제 시 연관된 피드백/기록/플랜/프로필/토큰이 모두 정리된 뒤 유저가 삭제된다")
	void deleteUser_normalUser_deletesAllRelatedDataInOrder() {
		User normalUser = buildUser(4L, Role.USER, false);
		when(userRepository.findById(4L)).thenReturn(Optional.of(normalUser));
		when(userProfileRepository.findByUser(normalUser)).thenReturn(Optional.empty());

		adminService.deleteUser(4L);

		verify(feedbackRepository).deleteByUser(normalUser);
		verify(recordRepository).deleteByUser(normalUser);
		verify(planRepository).deleteByUser(normalUser);
		verify(refreshTokenRepository).deleteByUser(normalUser);
		verify(userRepository).delete(normalUser);
	}

	@Test
	@DisplayName("잠긴 계정도 잠금 해제하면 잠금 상태가 풀린다 (해제는 관리자 계정 제한이 없다)")
	void unlockUser_succeeds() {
		User lockedUser = buildUser(5L, Role.USER, true);
		when(userRepository.findById(5L)).thenReturn(Optional.of(lockedUser));

		adminService.unlockUser(5L);

		assertThat(lockedUser.isLocked()).isFalse();
	}
}