package com.example.runmateaibackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.runmateaibackend.domain.plan.service.PlanService;
import com.example.runmateaibackend.domain.user.dto.ProfileRequest;
import com.example.runmateaibackend.domain.user.dto.ProfileResponse;
import com.example.runmateaibackend.domain.user.entity.Role;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;
import com.example.runmateaibackend.domain.user.repository.UserProfileRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import com.example.runmateaibackend.global.exception.ConflictException;
import com.example.runmateaibackend.global.exception.ResourceNotFoundException;

import jakarta.persistence.EntityManager;

/**
 * UserProfileService 단위 테스트.
 *
 * createProfile()은 "프로필 저장 → flush() → PlanService.createPlan() 호출"이
 * 순서대로 이어지는 게 핵심 로직이다 (PlanService가 같은 email로 유저/프로필을
 * 다시 조회하므로, flush 없이 넘어가면 방금 저장한 프로필이 안 보일 수 있다).
 * entityManager는 @PersistenceContext로 주입되는 필드라 생성자에 포함되지 않으므로,
 * 목 인스턴스를 만든 뒤 ReflectionTestUtils로 직접 주입한다.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserProfileRepository userProfileRepository;
	@Mock
	private PlanService planService;
	@Mock
	private EntityManager entityManager;

	private UserProfileService userProfileService;

	@BeforeEach
	void setUp() {
		userProfileService = new UserProfileService(userRepository, userProfileRepository, planService);
		ReflectionTestUtils.setField(userProfileService, "entityManager", entityManager);
	}

	private User buildUser(Long id, String email) {
		return User.builder()
			.id(id)
			.email(email)
			.password("encoded-password")
			.name("테스트유저")
			.role(Role.USER)
			.build();
	}

	private ProfileRequest buildRequest(String targetPace, int targetWeeklyRuns, String goalType,
		int targetWeeks, String fitnessLevel, BigDecimal monthlyGoalKm) {
		ProfileRequest request = new ProfileRequest();
		ReflectionTestUtils.setField(request, "targetPace", targetPace);
		ReflectionTestUtils.setField(request, "targetWeeklyRuns", targetWeeklyRuns);
		ReflectionTestUtils.setField(request, "goalType", goalType);
		ReflectionTestUtils.setField(request, "targetWeeks", targetWeeks);
		ReflectionTestUtils.setField(request, "fitnessLevel", fitnessLevel);
		ReflectionTestUtils.setField(request, "monthlyGoalKm", monthlyGoalKm);
		return request;
	}

	// ===== createProfile =====

	@Test
	@DisplayName("프로필을 정상 등록하면, 저장 후 flush를 거쳐 AI 훈련 플랜 생성까지 이어진다")
	void createProfile_success_savesFlushesAndCreatesPlan() {
		User user = buildUser(1L, "user@runmateai.com");
		when(userRepository.findByEmail("user@runmateai.com")).thenReturn(Optional.of(user));
		when(userProfileRepository.findByUser(user)).thenReturn(Optional.empty());

		ProfileRequest request = buildRequest("6'00\"", 3, "FIVE_K", 4, "BEGINNER", BigDecimal.valueOf(50));

		userProfileService.createProfile("user@runmateai.com", request);

		verify(userProfileRepository).save(any(UserProfile.class));
		// 저장 → flush → 플랜 생성이 반드시 이 순서로 이어져야 한다.
		// (flush 없이 plan 생성이 먼저 일어나면, PlanService가 방금 저장한 프로필을 못 찾는 버그로 이어진다.)
		var inOrder = org.mockito.Mockito.inOrder(userProfileRepository, entityManager, planService);
		inOrder.verify(userProfileRepository).save(any(UserProfile.class));
		inOrder.verify(entityManager).flush();
		inOrder.verify(planService).createPlan("user@runmateai.com");
	}

	@Test
	@DisplayName("이미 프로필이 등록되어 있으면 ConflictException이 발생하고, 저장/플랜생성 모두 일어나지 않는다")
	void createProfile_alreadyExists_throwsConflictException() {
		User user = buildUser(1L, "user@runmateai.com");
		when(userRepository.findByEmail("user@runmateai.com")).thenReturn(Optional.of(user));
		when(userProfileRepository.findByUser(user))
			.thenReturn(Optional.of(UserProfile.builder().user(user).build()));

		ProfileRequest request = buildRequest("6'00\"", 3, "FIVE_K", 4, "BEGINNER", null);

		assertThatThrownBy(() -> userProfileService.createProfile("user@runmateai.com", request))
			.isInstanceOf(ConflictException.class)
			.hasMessage("이미 프로필이 등록되어 있습니다.");

		verify(userProfileRepository, never()).save(any());
		verify(planService, never()).createPlan(any());
	}

	@Test
	@DisplayName("존재하지 않는 유저가 프로필을 등록하려 하면 ResourceNotFoundException이 발생한다")
	void createProfile_userNotFound_throwsResourceNotFoundException() {
		when(userRepository.findByEmail("nobody@runmateai.com")).thenReturn(Optional.empty());

		ProfileRequest request = buildRequest("6'00\"", 3, "FIVE_K", 4, "BEGINNER", null);

		assertThatThrownBy(() -> userProfileService.createProfile("nobody@runmateai.com", request))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("유저를 찾을 수 없습니다.");

		verify(userProfileRepository, never()).save(any());
	}

	// ===== getProfile =====

	@Test
	@DisplayName("등록된 프로필을 정상적으로 조회한다")
	void getProfile_success_returnsProfileResponse() {
		User user = buildUser(1L, "user@runmateai.com");
		UserProfile profile = UserProfile.builder()
			.user(user)
			.targetPace("6'00\"")
			.targetWeeklyRuns(3)
			.goalType("FIVE_K")
			.targetWeeks(4)
			.fitnessLevel("BEGINNER")
			.monthlyGoalKm(BigDecimal.valueOf(50))
			.build();

		when(userRepository.findByEmail("user@runmateai.com")).thenReturn(Optional.of(user));
		when(userProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));

		ProfileResponse response = userProfileService.getProfile("user@runmateai.com");

		assertThat(response.getTargetPace()).isEqualTo("6'00\"");
		assertThat(response.getTargetWeeklyRuns()).isEqualTo(3);
		assertThat(response.getGoalType()).isEqualTo("FIVE_K");
		assertThat(response.getMonthlyGoalKm()).isEqualByComparingTo(BigDecimal.valueOf(50));
	}

	@Test
	@DisplayName("등록된 프로필이 없으면 ResourceNotFoundException이 발생한다")
	void getProfile_noProfile_throwsResourceNotFoundException() {
		User user = buildUser(1L, "user@runmateai.com");
		when(userRepository.findByEmail("user@runmateai.com")).thenReturn(Optional.of(user));
		when(userProfileRepository.findByUser(user)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userProfileService.getProfile("user@runmateai.com"))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("등록된 프로필이 없습니다.");
	}

	@Test
	@DisplayName("존재하지 않는 유저의 프로필을 조회하면 ResourceNotFoundException이 발생한다")
	void getProfile_userNotFound_throwsResourceNotFoundException() {
		when(userRepository.findByEmail("nobody@runmateai.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userProfileService.getProfile("nobody@runmateai.com"))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("유저를 찾을 수 없습니다.");
	}

	// ===== updateProfile =====

	@Test
	@DisplayName("프로필을 정상적으로 수정하면, 엔티티의 값이 요청 내용으로 갱신된다")
	void updateProfile_success_updatesEntityFields() {
		User user = buildUser(1L, "user@runmateai.com");
		UserProfile profile = UserProfile.builder()
			.user(user)
			.targetPace("6'00\"")
			.targetWeeklyRuns(3)
			.goalType("FIVE_K")
			.targetWeeks(4)
			.fitnessLevel("BEGINNER")
			.monthlyGoalKm(BigDecimal.valueOf(50))
			.build();

		when(userRepository.findByEmail("user@runmateai.com")).thenReturn(Optional.of(user));
		when(userProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));

		ProfileRequest request = buildRequest("5'30\"", 5, "TEN_K", 8, "INTERMEDIATE", BigDecimal.valueOf(80));

		userProfileService.updateProfile("user@runmateai.com", request);

		// JPA dirty checking에 의존하는 구조라 별도 save() 호출은 없다.
		// 대신 엔티티 필드가 실제로 갱신됐는지 직접 확인한다.
		assertThat(profile.getTargetPace()).isEqualTo("5'30\"");
		assertThat(profile.getTargetWeeklyRuns()).isEqualTo(5);
		assertThat(profile.getGoalType()).isEqualTo("TEN_K");
		assertThat(profile.getTargetWeeks()).isEqualTo(8);
		assertThat(profile.getFitnessLevel()).isEqualTo("INTERMEDIATE");
		assertThat(profile.getMonthlyGoalKm()).isEqualByComparingTo(BigDecimal.valueOf(80));
	}

	@Test
	@DisplayName("등록된 프로필이 없는 상태에서 수정을 시도하면 ResourceNotFoundException이 발생한다")
	void updateProfile_noProfile_throwsResourceNotFoundException() {
		User user = buildUser(1L, "user@runmateai.com");
		when(userRepository.findByEmail("user@runmateai.com")).thenReturn(Optional.of(user));
		when(userProfileRepository.findByUser(user)).thenReturn(Optional.empty());

		ProfileRequest request = buildRequest("5'30\"", 5, "TEN_K", 8, "INTERMEDIATE", null);

		assertThatThrownBy(() -> userProfileService.updateProfile("user@runmateai.com", request))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("등록된 프로필이 없습니다.");
	}
}