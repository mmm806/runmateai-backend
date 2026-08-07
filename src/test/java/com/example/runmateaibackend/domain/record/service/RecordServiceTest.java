package com.example.runmateaibackend.domain.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.plan.service.PlanService;
import com.example.runmateaibackend.domain.record.dto.RecordRequest;
import com.example.runmateaibackend.domain.record.dto.RecordResponse;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.record.repository.RecordRepository;
import com.example.runmateaibackend.domain.user.entity.Role;
import com.example.runmateaibackend.domain.user.entity.User;
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
}