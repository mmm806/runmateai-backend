package com.example.runmateaibackend.domain.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.runmateaibackend.domain.feedback.entity.AiFeedback;
import com.example.runmateaibackend.domain.feedback.repository.FeedbackRepository;
import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.plan.service.PlanService;
import com.example.runmateaibackend.domain.record.dto.RecordRequest;
import com.example.runmateaibackend.domain.record.dto.RecordResponse;
import com.example.runmateaibackend.domain.record.dto.RecordStatsProjection;
import com.example.runmateaibackend.domain.record.dto.RecordStatsResponse;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.record.repository.RecordRepository;
import com.example.runmateaibackend.domain.user.entity.Role;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;
import com.example.runmateaibackend.domain.user.repository.UserProfileRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import com.example.runmateaibackend.global.exception.ConflictException;
import com.example.runmateaibackend.global.exception.ForbiddenException;
import com.example.runmateaibackend.global.exception.ResourceNotFoundException;

/**
 * RecordService 단위 테스트.
 *
 * getStats()의 통계 계산 로직은 리포지토리 집계 쿼리 의존이 많아 별도로 다루고,
 * 이번 테스트는 실제 사고 위험이 큰 소유권 검증(403)과 날짜 중복 검증(409),
 * 그리고 기록 삭제 시 완료 상태/피드백 정리 순서를 우선 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RecordServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PlanRepository planRepository;
	@Mock
	private RecordRepository recordRepository;
	@Mock
	private FeedbackRepository feedbackRepository;
	@Mock
	private UserProfileRepository userProfileRepository;
	@Mock
	private PlanService planService;

	private RecordService recordService;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		recordService = new RecordService(
			userRepository, planRepository, recordRepository, feedbackRepository,
			userProfileRepository, planService
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

	private RecordRequest buildRequest(LocalDate runDate, BigDecimal distanceKm) {
		RecordRequest request = new RecordRequest();
		ReflectionTestUtils.setField(request, "runDate", runDate);
		ReflectionTestUtils.setField(request, "distanceKm", distanceKm);
		ReflectionTestUtils.setField(request, "durationMin", 30);
		ReflectionTestUtils.setField(request, "avgPace", "6'00\"");
		return request;
	}

	@Test
	@DisplayName("같은 날짜에 이미 기록이 있으면 새 기록 생성 시 ConflictException이 발생한다")
	void createRecord_duplicateDate_throwsConflictException() {
		User user = buildUser(1L, "user@runmateai.com");
		LocalDate runDate = LocalDate.of(2026, 8, 1);
		when(userRepository.findByEmail("user@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.findByUserAndRunDate(user, runDate))
			.thenReturn(Optional.of(TrainingRecord.builder().build()));

		RecordRequest request = buildRequest(runDate, BigDecimal.valueOf(5));

		assertThatThrownBy(() -> recordService.createRecord("user@runmateai.com", request))
			.isInstanceOf(ConflictException.class)
			.hasMessage("해당 날짜에 이미 기록이 존재합니다.");

		verify(recordRepository, never()).save(any());
	}

	@Test
	@DisplayName("정상적으로 기록을 생성하면 저장 후 플랜 완료 여부 평가까지 이어진다")
	void createRecord_success_savesAndEvaluatesCompletion() {
		User user = buildUser(2L, "user2@runmateai.com");
		LocalDate runDate = LocalDate.of(2026, 8, 2);
		when(userRepository.findByEmail("user2@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.findByUserAndRunDate(user, runDate)).thenReturn(Optional.empty());
		when(planRepository.findFirstByUserAndIsActiveOrderByCreatedAtDesc(user, true))
			.thenReturn(Optional.empty());

		RecordRequest request = buildRequest(runDate, BigDecimal.valueOf(5));

		RecordResponse response = recordService.createRecord("user2@runmateai.com", request);

		assertThat(response).isNotNull();
		verify(recordRepository).save(any(TrainingRecord.class));
		// 기록 저장 후 반드시 완료 여부 자동 평가로 이어져야 한다 (오늘 만든 evaluateCompletion 연동).
		verify(planService).evaluateCompletion(any(TrainingRecord.class));
	}

	@Test
	@DisplayName("본인 소유가 아닌 기록을 수정하려 하면 ForbiddenException이 발생한다")
	void updateRecord_notOwner_throwsForbiddenException() {
		User owner = buildUser(10L, "owner@runmateai.com");
		User attacker = buildUser(20L, "attacker@runmateai.com");

		TrainingRecord othersRecord = TrainingRecord.builder()
			.id(100L)
			.user(owner)
			.runDate(LocalDate.of(2026, 8, 1))
			.distanceKm(BigDecimal.valueOf(5))
			.build();

		when(userRepository.findByEmail("attacker@runmateai.com")).thenReturn(Optional.of(attacker));
		when(recordRepository.findById(100L)).thenReturn(Optional.of(othersRecord));

		RecordRequest request = buildRequest(LocalDate.of(2026, 8, 1), BigDecimal.valueOf(10));

		assertThatThrownBy(() -> recordService.updateRecord("attacker@runmateai.com", 100L, request))
			.isInstanceOf(ForbiddenException.class)
			.hasMessage("본인의 기록만 수정할 수 있습니다.");
	}

	@Test
	@DisplayName("수정 시 날짜를 이미 기록이 있는 다른 날짜로 바꾸면 ConflictException이 발생한다")
	void updateRecord_changeToConflictingDate_throwsConflictException() {
		User user = buildUser(30L, "user3@runmateai.com");
		LocalDate originalDate = LocalDate.of(2026, 8, 1);
		LocalDate conflictingDate = LocalDate.of(2026, 8, 2);

		TrainingRecord myRecord = TrainingRecord.builder()
			.id(200L)
			.user(user)
			.runDate(originalDate)
			.distanceKm(BigDecimal.valueOf(5))
			.build();

		when(userRepository.findByEmail("user3@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.findById(200L)).thenReturn(Optional.of(myRecord));
		when(recordRepository.findByUserAndRunDate(user, conflictingDate))
			.thenReturn(Optional.of(TrainingRecord.builder().id(999L).build()));

		RecordRequest request = buildRequest(conflictingDate, BigDecimal.valueOf(7));

		assertThatThrownBy(() -> recordService.updateRecord("user3@runmateai.com", 200L, request))
			.isInstanceOf(ConflictException.class)
			.hasMessage("해당 날짜에 이미 다른 기록이 존재합니다.");
	}

	@Test
	@DisplayName("본인 소유가 아닌 기록을 삭제하려 하면 ForbiddenException이 발생하고 삭제되지 않는다")
	void deleteRecord_notOwner_throwsForbiddenException() {
		User owner = buildUser(40L, "owner2@runmateai.com");
		User attacker = buildUser(50L, "attacker2@runmateai.com");

		TrainingRecord othersRecord = TrainingRecord.builder()
			.id(300L)
			.user(owner)
			.runDate(LocalDate.of(2026, 8, 1))
			.build();

		when(userRepository.findByEmail("attacker2@runmateai.com")).thenReturn(Optional.of(attacker));
		when(recordRepository.findById(300L)).thenReturn(Optional.of(othersRecord));

		assertThatThrownBy(() -> recordService.deleteRecord("attacker2@runmateai.com", 300L))
			.isInstanceOf(ForbiddenException.class)
			.hasMessage("본인의 기록만 삭제할 수 있습니다.");

		verify(recordRepository, never()).delete(any());
	}

	@Test
	@DisplayName("기록 삭제 시, 그 기록으로 평가됐던 플랜 완료 상태를 먼저 되돌린 뒤 기록을 삭제한다")
	void deleteRecord_success_revertsCompletionBeforeDeleting() {
		User user = buildUser(60L, "user4@runmateai.com");
		TrainingRecord myRecord = TrainingRecord.builder()
			.id(400L)
			.user(user)
			.runDate(LocalDate.of(2026, 8, 1))
			.build();

		when(userRepository.findByEmail("user4@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.findById(400L)).thenReturn(Optional.of(myRecord));
		when(feedbackRepository.findByTrainingRecordId(400L)).thenReturn(List.of());

		recordService.deleteRecord("user4@runmateai.com", 400L);

		verify(planService).revertCompletionForRecord(myRecord);
		verify(feedbackRepository).deleteByTrainingRecord(myRecord);
		verify(recordRepository).delete(myRecord);
	}

	@Test
	@DisplayName("삭제하는 기록이 플랜 자동 조정을 유발한 AI 피드백과 연결되어 있었다면, " +
		"삭제 후 최근 활성 플랜을 비활성화하고 이전 플랜 갱신 상태로 되돌린다")
	void deleteRecord_whenLinkedToPlanUpdateFeedback_revertsToLatestPlanUpdate() {
		User user = buildUser(70L, "user5@runmateai.com");
		TrainingRecord myRecord = TrainingRecord.builder()
			.id(500L)
			.user(user)
			.runDate(LocalDate.of(2026, 8, 1))
			.build();

		AiFeedback planUpdateFeedback = AiFeedback.builder()
			.planUpdated(true)
			.build();

		when(userRepository.findByEmail("user5@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.findById(500L)).thenReturn(Optional.of(myRecord));
		when(feedbackRepository.findByTrainingRecordId(500L)).thenReturn(List.of(planUpdateFeedback));
		when(planRepository.findFirstByUserAndIsActiveOrderByCreatedAtDesc(user, true))
			.thenReturn(Optional.empty());
		when(feedbackRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());
		when(planRepository.findFirstByUserOrderByCreatedAtAsc(user)).thenReturn(Optional.empty());

		recordService.deleteRecord("user5@runmateai.com", 500L);

		// 삭제 자체는 정상적으로 진행되어야 한다.
		verify(recordRepository).delete(myRecord);
		// 이 기록이 plan_updated=true인 피드백과 연결되어 있었으므로,
		// 활성 플랜 재조정 로직(revertToLatestPlanUpdate)까지 이어져야 한다.
		verify(planRepository).findFirstByUserAndIsActiveOrderByCreatedAtDesc(user, true);
		verify(feedbackRepository).findByUserOrderByCreatedAtDesc(user);
	}

	@Test
	@DisplayName("존재하지 않는 날짜로 기록 조회 시 ResourceNotFoundException이 발생한다")
	void getRecordByDate_notFound_throwsResourceNotFoundException() {
		User user = buildUser(80L, "user6@runmateai.com");
		LocalDate date = LocalDate.of(2026, 8, 1);
		when(userRepository.findByEmail("user6@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.findByUserAndRunDate(user, date)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> recordService.getRecordByDate("user6@runmateai.com", date))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("해당 날짜에 기록이 없습니다.");
	}

	@Test
	@DisplayName("기록 삭제로 인한 플랜 재조정 시, 마지막 플랜 갱신 피드백이 있으면 " +
		"그 피드백에 연결된 플랜을 재활성화한다 (가장 오래된 플랜으로 폴백하는 분기는 타지 않는다)")
	void deleteRecord_revertToLatestPlanUpdate_reactivatesPlanFromLatestFeedback() {
		User user = buildUser(90L, "user7@runmateai.com");
		TrainingRecord myRecord = TrainingRecord.builder()
			.id(600L).user(user).runDate(LocalDate.of(2026, 8, 1)).build();

		AiFeedback deletedRecordFeedback = AiFeedback.builder().planUpdated(true).build();

		TrainingPlan currentActivePlan = TrainingPlan.builder()
			.id(1L).user(user).planData("{}").goalType("FIVE_K")
			.startDate(LocalDate.of(2026, 7, 1)).isActive(true).build();
		TrainingPlan planToReactivate = TrainingPlan.builder()
			.id(2L).user(user).planData("{}").goalType("FIVE_K")
			.startDate(LocalDate.of(2026, 6, 1)).isActive(false).build();
		AiFeedback latestPlanUpdateFeedback = AiFeedback.builder()
			.planUpdated(true).trainingPlan(planToReactivate).build();

		when(userRepository.findByEmail("user7@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.findById(600L)).thenReturn(Optional.of(myRecord));
		when(feedbackRepository.findByTrainingRecordId(600L)).thenReturn(List.of(deletedRecordFeedback));
		when(planRepository.findFirstByUserAndIsActiveOrderByCreatedAtDesc(user, true))
			.thenReturn(Optional.of(currentActivePlan));
		when(feedbackRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(latestPlanUpdateFeedback));

		recordService.deleteRecord("user7@runmateai.com", 600L);

		assertThat(currentActivePlan.isActive()).isFalse(); // 기존 활성 플랜은 비활성화된다.
		assertThat(planToReactivate.isActive()).isTrue();  // 마지막 갱신 피드백에 연결된 플랜이 재활성화된다.
		// "마지막 갱신 피드백이 없는 경우"의 폴백 분기(가장 오래된 플랜 재활성화)는 타지 않아야 한다.
		verify(planRepository, never()).findFirstByUserOrderByCreatedAtAsc(any());
	}

	// ===== getStats =====

	/**
	 * getStats() 관련 테스트들이 공통으로 필요로 하는 리포지토리 목을 최소한으로 세팅한다.
	 * 스트릭/증감률처럼 개별 테스트가 검증하려는 값만 파라미터로 받고,
	 * 그 외 값은 lenient()로 느슨하게 스텁해서 "이 테스트가 실제로 검증하려는 게 무엇인지"를
	 * 각 테스트 본문에서 뚜렷하게 드러나게 했다.
	 */
	private void stubMinimalStatsMocks(User user, List<LocalDate> runDates,
		BigDecimal thisMonthDistance, BigDecimal lastMonthDistance) {

		RecordStatsProjection projection = mock(RecordStatsProjection.class);
		lenient().when(projection.getTotalRuns()).thenReturn(5L);
		lenient().when(projection.getTotalDistance()).thenReturn(BigDecimal.valueOf(25));
		lenient().when(projection.getTotalDuration()).thenReturn(140L);
		lenient().when(projection.getLongestDistance()).thenReturn(BigDecimal.valueOf(8));
		lenient().when(projection.getLongestDuration()).thenReturn(40);
		lenient().when(projection.getAvgHeartRate()).thenReturn(150.0);
		lenient().when(projection.getTotalCalories()).thenReturn(1200L);
		lenient().when(projection.getTotalElevationGain()).thenReturn(300L);

		YearMonth thisMonth = YearMonth.now();
		YearMonth lastMonth = thisMonth.minusMonths(1);

		lenient().when(recordRepository.countByUser(user)).thenReturn(5L);
		lenient().when(recordRepository.findAggregatedStatsByUser(user)).thenReturn(projection);
		lenient().when(recordRepository.countByFeeling(user)).thenReturn(List.of());
		lenient().when(recordRepository.sumDistanceByUserAndMonth(user, thisMonth.getYear(), thisMonth.getMonthValue()))
			.thenReturn(thisMonthDistance);
		lenient().when(recordRepository.sumDistanceByUserAndMonth(user, lastMonth.getYear(), lastMonth.getMonthValue()))
			.thenReturn(lastMonthDistance);
		lenient().when(recordRepository.findRunDatesByUser(user)).thenReturn(runDates);
		lenient().when(recordRepository.findBestPaceRecordByUserId(user.getId())).thenReturn(Optional.empty());
		lenient().when(feedbackRepository.countByUserAndPlanUpdatedTrue(user)).thenReturn(2L);
		lenient().when(recordRepository.findFirstByUserAndDistanceKmBetweenOrderByDurationMinAsc(eq(user), any(), any()))
			.thenReturn(Optional.empty());
		lenient().when(userProfileRepository.findByUser(user)).thenReturn(Optional.empty());
	}

	@Test
	@DisplayName("기록이 하나도 없는 유저는 예외 대신 0으로 채워진 빈 통계를 받는다")
	void getStats_noRecords_noProfile_returnsEmptyStats() {
		User user = buildUser(100L, "stats1@runmateai.com");
		when(userRepository.findByEmail("stats1@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.countByUser(user)).thenReturn(0L);
		when(userProfileRepository.findByUser(user)).thenReturn(Optional.empty());

		RecordStatsResponse response = recordService.getStats("stats1@runmateai.com");

		assertThat(response.getTotalRuns()).isZero();
		assertThat(response.getTotalDistanceKm()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(response.getAvgPace()).isEqualTo("-");
		assertThat(response.getFeelingDistribution()).isEmpty();
		assertThat(response.getMonthlyGoalKm()).isNull();
		assertThat(response.getMonthlyGoalProgressPercent()).isNull();
		// 기록이 없으면 무거운 집계 쿼리 자체를 호출하지 않아야 한다 (빈 결과를 만들려고 DB를 두드릴 필요는 없다).
		verify(recordRepository, never()).findAggregatedStatsByUser(any());
	}

	@Test
	@DisplayName("기록은 없지만 프로필의 월간 목표가 등록되어 있으면, 목표값은 채우고 진행률은 0으로 반환한다")
	void getStats_noRecords_withProfile_returnsMonthlyGoalWithZeroProgress() {
		User user = buildUser(101L, "stats2@runmateai.com");
		UserProfile profile = UserProfile.builder().user(user).monthlyGoalKm(BigDecimal.valueOf(50)).build();

		when(userRepository.findByEmail("stats2@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.countByUser(user)).thenReturn(0L);
		when(userProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));

		RecordStatsResponse response = recordService.getStats("stats2@runmateai.com");

		assertThat(response.getMonthlyGoalKm()).isEqualByComparingTo(BigDecimal.valueOf(50));
		assertThat(response.getMonthlyGoalProgressPercent()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	@DisplayName("기록이 있는 유저의 통계를 조회하면, 집계 쿼리 결과를 기반으로 모든 필드를 계산해 반환한다")
	void getStats_withRecords_computesAggregatedStats() {
		User user = buildUser(102L, "stats3@runmateai.com");

		RecordStatsProjection projection = mock(RecordStatsProjection.class);
		when(projection.getTotalRuns()).thenReturn(5L);
		when(projection.getTotalDistance()).thenReturn(BigDecimal.valueOf(25));
		when(projection.getTotalDuration()).thenReturn(140L);
		when(projection.getLongestDistance()).thenReturn(BigDecimal.valueOf(8));
		when(projection.getLongestDuration()).thenReturn(40);
		when(projection.getAvgHeartRate()).thenReturn(150.0);
		when(projection.getTotalCalories()).thenReturn(1200L);
		when(projection.getTotalElevationGain()).thenReturn(300L);

		YearMonth thisMonth = YearMonth.now();
		YearMonth lastMonth = thisMonth.minusMonths(1);

		when(userRepository.findByEmail("stats3@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.countByUser(user)).thenReturn(5L);
		when(recordRepository.findAggregatedStatsByUser(user)).thenReturn(projection);
		when(recordRepository.countByFeeling(user)).thenReturn(
			List.of(new Object[] { "good", 3L }, new Object[] { "tired", 2L })
		);
		when(recordRepository.sumDistanceByUserAndMonth(user, thisMonth.getYear(), thisMonth.getMonthValue()))
			.thenReturn(BigDecimal.valueOf(15));
		when(recordRepository.sumDistanceByUserAndMonth(user, lastMonth.getYear(), lastMonth.getMonthValue()))
			.thenReturn(BigDecimal.valueOf(10));
		when(recordRepository.findRunDatesByUser(user)).thenReturn(List.of(LocalDate.now()));
		when(recordRepository.findBestPaceRecordByUserId(user.getId())).thenReturn(Optional.empty());
		when(feedbackRepository.countByUserAndPlanUpdatedTrue(user)).thenReturn(2L);
		when(recordRepository.findFirstByUserAndDistanceKmBetweenOrderByDurationMinAsc(eq(user), any(), any()))
			.thenReturn(Optional.empty());
		when(userProfileRepository.findByUser(user)).thenReturn(
			Optional.of(UserProfile.builder().user(user).monthlyGoalKm(BigDecimal.valueOf(100)).build())
		);

		RecordStatsResponse response = recordService.getStats("stats3@runmateai.com");

		assertThat(response.getTotalRuns()).isEqualTo(5);
		assertThat(response.getTotalDistanceKm()).isEqualByComparingTo(BigDecimal.valueOf(25));
		assertThat(response.getTotalDurationMin()).isEqualTo(140);
		assertThat(response.getAvgPace()).isEqualTo("5'36\""); // 140분 / 25km = 5.6분/km
		assertThat(response.getLongestDistanceKm()).isEqualByComparingTo(BigDecimal.valueOf(8));
		assertThat(response.getBestPace()).isEqualTo("-"); // 최고 페이스 기록 없음
		assertThat(response.getLongestDurationMin()).isEqualTo(40);
		assertThat(response.getFeelingDistribution()).containsEntry("good", 3).containsEntry("tired", 2);
		assertThat(response.getTotalPlanUpdates()).isEqualTo(2);
		assertThat(response.getThisMonthDistanceKm()).isEqualByComparingTo(BigDecimal.valueOf(15));
		assertThat(response.getLastMonthDistanceKm()).isEqualByComparingTo(BigDecimal.valueOf(10));
		assertThat(response.getDistanceChangePercent()).isEqualTo(50.0); // (15-10)/10 * 100
		assertThat(response.getAvgHeartRate()).isEqualTo(150);
		assertThat(response.getTotalCalories()).isEqualTo(1200);
		assertThat(response.getMonthlyGoalKm()).isEqualByComparingTo(BigDecimal.valueOf(100));
		assertThat(response.getMonthlyGoalProgressPercent()).isEqualByComparingTo(BigDecimal.valueOf(15)); // 15/100*100
		assertThat(response.getBestRecordsByGoalType()).isEmpty();
		assertThat(response.getTotalElevationGain()).isEqualTo(300);
	}

	@Test
	@DisplayName("가장 최근 기록이 오늘 또는 어제이면서 연속으로 이어져 있으면, 그 연속 일수가 현재/최장 스트릭이 된다")
	void getStats_streakEndingToday_currentStreakEqualsConsecutiveDays() {
		User user = buildUser(103L, "stats4@runmateai.com");
		LocalDate today = LocalDate.now();
		List<LocalDate> threeConsecutiveDaysEndingToday = List.of(today, today.minusDays(1), today.minusDays(2));

		when(userRepository.findByEmail("stats4@runmateai.com")).thenReturn(Optional.of(user));
		stubMinimalStatsMocks(user, threeConsecutiveDaysEndingToday, BigDecimal.valueOf(15), BigDecimal.valueOf(10));

		RecordStatsResponse response = recordService.getStats("stats4@runmateai.com");

		assertThat(response.getCurrentStreak()).isEqualTo(3);
		assertThat(response.getLongestStreak()).isEqualTo(3);
	}

	@Test
	@DisplayName("가장 최근 기록이 오늘도 어제도 아니면, 과거에 연속 기록이 있었어도 현재 스트릭은 0이다")
	void getStats_streakNotEndingToday_currentStreakIsZero() {
		User user = buildUser(104L, "stats5@runmateai.com");
		LocalDate today = LocalDate.now();
		List<LocalDate> threeConsecutiveDaysThreeDaysAgo = List.of(
			today.minusDays(3), today.minusDays(4), today.minusDays(5)
		);

		when(userRepository.findByEmail("stats5@runmateai.com")).thenReturn(Optional.of(user));
		stubMinimalStatsMocks(user, threeConsecutiveDaysThreeDaysAgo, BigDecimal.valueOf(15), BigDecimal.valueOf(10));

		RecordStatsResponse response = recordService.getStats("stats5@runmateai.com");

		assertThat(response.getCurrentStreak()).isZero();
		assertThat(response.getLongestStreak()).isEqualTo(3); // 최장 스트릭 자체는 과거 기록 기준으로 유지된다.
	}

	@Test
	@DisplayName("지난달 누적 거리가 0이면, 0으로 나누는 대신 증감률을 null로 반환한다")
	void getStats_lastMonthDistanceZero_distanceChangePercentIsNull() {
		User user = buildUser(105L, "stats6@runmateai.com");

		when(userRepository.findByEmail("stats6@runmateai.com")).thenReturn(Optional.of(user));
		stubMinimalStatsMocks(user, List.of(LocalDate.now()), BigDecimal.valueOf(15), BigDecimal.ZERO);

		RecordStatsResponse response = recordService.getStats("stats6@runmateai.com");

		assertThat(response.getDistanceChangePercent()).isNull();
	}
}