package com.example.runmateaibackend.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.user.entity.Role;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;

/**
 * PlanTransactionSupport 단위 테스트.
 *
 * PlanService.createPlan()이 "① 기존 플랜 비활성화(짧은 트랜잭션) → ② Claude API 호출
 * (트랜잭션 없음) → ③ 새 플랜 저장(짧은 트랜잭션)"으로 쪼갠 이유가 이 클래스에 담겨 있다.
 * 여기서는 그 짧은 트랜잭션 두 개 각각이 올바르게 동작하는지만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PlanTransactionSupportTest {

	@Mock
	private PlanRepository planRepository;

	private PlanTransactionSupport planTransactionSupport;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		planTransactionSupport = new PlanTransactionSupport(planRepository);
	}

	private User buildUser(Long id, String email) {
		return User.builder().id(id).email(email).password("encoded").name("테스트유저").role(Role.USER).build();
	}

	@Test
	@DisplayName("기존 활성 플랜이 있으면 비활성화 처리 후 저장한다")
	void deactivateExistingActivePlan_whenActivePlanExists_deactivatesAndSaves() {
		User user = buildUser(1L, "user@runmateai.com");
		TrainingPlan activePlan = TrainingPlan.builder()
			.id(10L).user(user).planData("{}").goalType("FIVE_K")
			.startDate(LocalDate.of(2026, 1, 1)).isActive(true).build();

		when(planRepository.findFirstByUserAndIsActiveOrderByCreatedAtDesc(user, true))
			.thenReturn(Optional.of(activePlan));

		planTransactionSupport.deactivateExistingActivePlan(user);

		assertThat(activePlan.isActive()).isFalse();
		verify(planRepository).save(activePlan);
	}

	@Test
	@DisplayName("기존 활성 플랜이 없으면 아무 것도 저장하지 않는다")
	void deactivateExistingActivePlan_whenNoActivePlan_doesNothing() {
		User user = buildUser(2L, "user2@runmateai.com");
		when(planRepository.findFirstByUserAndIsActiveOrderByCreatedAtDesc(user, true))
			.thenReturn(Optional.empty());

		planTransactionSupport.deactivateExistingActivePlan(user);

		verify(planRepository, never()).save(any());
	}

	@Test
	@DisplayName("새 플랜을 저장하면, 시작일이 내일로 설정되고 활성 상태로 생성된다")
	void saveNewPlan_buildsActivePlanStartingTomorrow() {
		User user = buildUser(3L, "user3@runmateai.com");
		UserProfile profile = UserProfile.builder()
			.user(user).targetPace("6'00\"").targetWeeklyRuns(3).goalType("TEN_K")
			.targetWeeks(6).fitnessLevel("INTERMEDIATE").monthlyGoalKm(BigDecimal.valueOf(60)).build();
		String planDataJson = "{\"weeks\":[]}";

		when(planRepository.save(any(TrainingPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TrainingPlan result = planTransactionSupport.saveNewPlan(user, profile, planDataJson);

		ArgumentCaptor<TrainingPlan> captor = ArgumentCaptor.forClass(TrainingPlan.class);
		verify(planRepository).save(captor.capture());
		TrainingPlan saved = captor.getValue();

		assertThat(saved.getUser()).isEqualTo(user);
		assertThat(saved.getPlanData()).isEqualTo(planDataJson);
		assertThat(saved.getGoalType()).isEqualTo("TEN_K"); // 프로필의 goalType을 그대로 사용
		assertThat(saved.getStartDate()).isEqualTo(LocalDate.now().plusDays(1)); // 1주차 1일째 = 내일
		assertThat(saved.isActive()).isTrue();
		assertThat(result).isEqualTo(saved);
	}
}