package com.example.runmateaibackend.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.runmateaibackend.domain.plan.dto.PlanResponse;
import com.example.runmateaibackend.domain.plan.entity.PlanDayProgress;
import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanDayProgressRepository;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.user.entity.Role;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.repository.UserProfileRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import com.example.runmateaibackend.global.client.ClaudeApiClient;
import com.example.runmateaibackend.global.exception.ResourceNotFoundException;

/**
 * PlanService 단위 테스트.
 *
 * createPlan()의 동시성/락 로직은 PlanServiceConcurrencyTest(Testcontainers 통합 테스트)에서
 * 이미 다루고 있으므로, 이번 테스트는 아직 커버되지 않았던 나머지 세 메서드를 검증한다.
 * - getActivePlan(): 조회 대상이 없을 때의 예외 처리
 * - evaluateCompletion(): 러닝 기록 → (주차, 일자) 환산 → 완료 여부 자동 판정의 분기 로직
 * - revertCompletionForRecord(): 기록 삭제 시 완료 상태를 되돌리는 로직
 *
 * PlanDayLookup은 외부 의존성 없이 planData(JSON) 문자열만 파싱하는 순수 컴포넌트라
 * 목(mock)으로 대체하지 않고 실제 인스턴스를 사용했다. 목으로 대체하면 매번 JSON 구조를
 * 흉내 낸 stub을 작성해야 하는데, 그러면 정작 "JSON에서 정확한 날짜를 찾아내는지"는
 * 검증하지 못한 채 테스트가 통과해버릴 수 있기 때문이다.
 */
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserProfileRepository userProfileRepository;
	@Mock
	private PlanRepository planRepository;
	@Mock
	private PlanDayProgressRepository planDayProgressRepository;
	@Mock
	private ClaudeApiClient claudeApiClient;
	@Mock
	private PlanPromptBuilder planPromptBuilder;
	@Mock
	private PlanTransactionSupport planTransactionSupport;

	private PlanService planService;

	// 1주차 1일째(easy run, 5km)와 1주차 2일째(휴식일)를 가진 4주짜리 플랜 데이터.
	private static final String PLAN_DATA_JSON = """
		{
		  "weeks": [
		    {
		      "week": 1,
		      "days": [
		        { "day": 1, "type": "easy run", "distance": 5, "pace": "6'00\\"" },
		        { "day": 2, "type": "rest" }
		      ]
		    }
		  ]
		}
		""";

	@BeforeEach
	void setUp() {
		// PlanDayLookup은 의존성이 없는 순수 컴포넌트이므로 목 대신 실제 객체를 사용한다.
		planService = new PlanService(
			userRepository, userProfileRepository, planRepository, planDayProgressRepository,
			claudeApiClient, planPromptBuilder, new PlanDayLookup(), planTransactionSupport
		);
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

	private TrainingPlan buildPlan(Long id, User user, LocalDate startDate) {
		return TrainingPlan.builder()
			.id(id)
			.user(user)
			.planData(PLAN_DATA_JSON)
			.goalType("FIVE_K")
			.startDate(startDate)
			.isActive(true)
			.build();
	}

	// ===== getActivePlan =====

	@Test
	@DisplayName("존재하지 않는 유저로 활성 플랜을 조회하면 ResourceNotFoundException이 발생한다")
	void getActivePlan_userNotFound_throwsResourceNotFoundException() {
		when(userRepository.findByEmail("nobody@runmateai.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> planService.getActivePlan("nobody@runmateai.com"))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("유저를 찾을 수 없습니다.");

		verifyNoInteractions(planRepository);
	}

	@Test
	@DisplayName("활성 플랜이 없는 유저가 조회하면 ResourceNotFoundException이 발생한다")
	void getActivePlan_noActivePlan_throwsResourceNotFoundException() {
		User user = buildUser(1L, "user@runmateai.com");
		when(userRepository.findByEmail("user@runmateai.com")).thenReturn(Optional.of(user));
		when(planRepository.findFirstByUserAndIsActiveOrderByCreatedAtDesc(user, true))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> planService.getActivePlan("user@runmateai.com"))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("활성화된 플랜이 없습니다.");
	}

	@Test
	@DisplayName("활성 플랜 조회에 성공하면 일자별 진행 상태 목록까지 함께 반환한다")
	void getActivePlan_success_returnsPlanWithProgress() {
		User user = buildUser(2L, "user2@runmateai.com");
		TrainingPlan plan = buildPlan(10L, user, LocalDate.of(2026, 8, 1));

		PlanDayProgress day1 = PlanDayProgress.builder()
			.id(100L).trainingPlan(plan).weekNumber(1).dayNumber(1).completed(true).build();
		PlanDayProgress day2 = PlanDayProgress.builder()
			.id(101L).trainingPlan(plan).weekNumber(1).dayNumber(2).completed(false).build();

		when(userRepository.findByEmail("user2@runmateai.com")).thenReturn(Optional.of(user));
		when(planRepository.findFirstByUserAndIsActiveOrderByCreatedAtDesc(user, true))
			.thenReturn(Optional.of(plan));
		when(planDayProgressRepository.findByTrainingPlan(plan)).thenReturn(List.of(day1, day2));

		PlanResponse response = planService.getActivePlan("user2@runmateai.com");

		assertThat(response.getId()).isEqualTo(10L);
		assertThat(response.getProgress()).hasSize(2);
		assertThat(response.getProgress().get(0).isCompleted()).isTrue();
		assertThat(response.getProgress().get(1).isCompleted()).isFalse();
	}

	// ===== evaluateCompletion =====

	@Test
	@DisplayName("기록에 연결된 플랜이 없으면 아무 처리도 하지 않는다")
	void evaluateCompletion_noLinkedPlan_doesNothing() {
		TrainingRecord record = TrainingRecord.builder()
			.id(1L)
			.runDate(LocalDate.of(2026, 8, 1))
			.distanceKm(BigDecimal.valueOf(5))
			.trainingPlan(null)
			.build();

		planService.evaluateCompletion(record);

		verifyNoInteractions(planDayProgressRepository);
	}

	@Test
	@DisplayName("플랜 시작일 이전 날짜의 기록이면 아무 처리도 하지 않는다")
	void evaluateCompletion_beforePlanStartDate_doesNothing() {
		TrainingPlan plan = buildPlan(10L, buildUser(1L, "u@runmateai.com"), LocalDate.of(2026, 8, 10));
		TrainingRecord record = TrainingRecord.builder()
			.id(1L)
			.runDate(LocalDate.of(2026, 8, 1)) // 시작일보다 이전
			.distanceKm(BigDecimal.valueOf(5))
			.trainingPlan(plan)
			.build();

		planService.evaluateCompletion(record);

		verifyNoInteractions(planDayProgressRepository);
	}

	@Test
	@DisplayName("휴식일에 해당하는 기록이면 완료 평가 대상에서 제외한다")
	void evaluateCompletion_restDay_doesNothing() {
		User user = buildUser(1L, "u@runmateai.com");
		TrainingPlan plan = buildPlan(10L, user, LocalDate.of(2026, 8, 1));
		TrainingRecord record = TrainingRecord.builder()
			.id(1L)
			.runDate(LocalDate.of(2026, 8, 2)) // 1주차 2일째 = 휴식일
			.distanceKm(BigDecimal.valueOf(5))
			.trainingPlan(plan)
			.build();

		planService.evaluateCompletion(record);

		verifyNoInteractions(planDayProgressRepository);
	}

	@Test
	@DisplayName("목표 거리 이상 뛰었고 기존 진행 상태가 없으면, 새로 생성하며 완료로 표시한다")
	void evaluateCompletion_achievedWithNoExistingProgress_createsAndMarksCompleted() {
		User user = buildUser(1L, "u@runmateai.com");
		TrainingPlan plan = buildPlan(10L, user, LocalDate.of(2026, 8, 1));
		TrainingRecord record = TrainingRecord.builder()
			.id(1L)
			.runDate(LocalDate.of(2026, 8, 1)) // 1주차 1일째, 목표 5km
			.distanceKm(BigDecimal.valueOf(6)) // 목표보다 더 뜀
			.trainingPlan(plan)
			.build();

		when(planDayProgressRepository.findByTrainingPlanAndWeekNumberAndDayNumber(plan, 1, 1))
			.thenReturn(Optional.empty());
		when(planDayProgressRepository.save(any(PlanDayProgress.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		planService.evaluateCompletion(record);

		verify(planDayProgressRepository).save(any(PlanDayProgress.class));
	}

	@Test
	@DisplayName("목표 거리보다 적게 뛰었고 기존 진행 상태가 있으면, 새로 만들지 않고 미완료로 갱신한다")
	void evaluateCompletion_notAchievedWithExistingProgress_marksIncompleteWithoutCreating() {
		User user = buildUser(1L, "u@runmateai.com");
		TrainingPlan plan = buildPlan(10L, user, LocalDate.of(2026, 8, 1));
		TrainingRecord record = TrainingRecord.builder()
			.id(1L)
			.runDate(LocalDate.of(2026, 8, 1)) // 1주차 1일째, 목표 5km
			.distanceKm(BigDecimal.valueOf(3)) // 목표에 미달
			.trainingPlan(plan)
			.build();

		PlanDayProgress existing = PlanDayProgress.builder()
			.id(200L).trainingPlan(plan).weekNumber(1).dayNumber(1).completed(true).build();

		when(planDayProgressRepository.findByTrainingPlanAndWeekNumberAndDayNumber(plan, 1, 1))
			.thenReturn(Optional.of(existing));

		planService.evaluateCompletion(record);

		assertThat(existing.isCompleted()).isFalse();
		assertThat(existing.getTriggeringRecord()).isEqualTo(record);
		assertThat(existing.getCompletedAt()).isNull();
		verify(planDayProgressRepository, never()).save(any());
	}

	// ===== revertCompletionForRecord =====

	@Test
	@DisplayName("기록 삭제 시, 그 기록으로 완료 처리됐던 진행 상태를 전부 초기화한다")
	void revertCompletionForRecord_clearsAllLinkedProgress() {
		TrainingRecord record = TrainingRecord.builder().id(1L).build();

		PlanDayProgress progress1 = PlanDayProgress.builder()
			.id(1L).weekNumber(1).dayNumber(1)
			.completed(true).triggeringRecord(record).build();
		PlanDayProgress progress2 = PlanDayProgress.builder()
			.id(2L).weekNumber(1).dayNumber(2)
			.completed(false).triggeringRecord(record).build();

		when(planDayProgressRepository.findByTriggeringRecord(record))
			.thenReturn(List.of(progress1, progress2));

		planService.revertCompletionForRecord(record);

		assertThat(progress1.isCompleted()).isFalse();
		assertThat(progress1.getTriggeringRecord()).isNull();
		assertThat(progress2.isCompleted()).isFalse();
		assertThat(progress2.getTriggeringRecord()).isNull();
	}

	@Test
	@DisplayName("연결된 진행 상태가 없으면 예외 없이 아무 일도 일어나지 않는다")
	void revertCompletionForRecord_noLinkedProgress_doesNothing() {
		TrainingRecord record = TrainingRecord.builder().id(1L).build();
		when(planDayProgressRepository.findByTriggeringRecord(record)).thenReturn(List.of());

		planService.revertCompletionForRecord(record);

		verify(planDayProgressRepository).findByTriggeringRecord(record);
	}
}