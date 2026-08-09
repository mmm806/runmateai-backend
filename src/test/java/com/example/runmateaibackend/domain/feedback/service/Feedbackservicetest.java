package com.example.runmateaibackend.domain.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.runmateaibackend.domain.feedback.dto.AiFeedbackResult;
import com.example.runmateaibackend.domain.feedback.dto.FeedbackResponse;
import com.example.runmateaibackend.domain.feedback.repository.FeedbackRepository;
import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanDayProgressRepository;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.plan.service.PlanDataMerger;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.record.repository.RecordRepository;
import com.example.runmateaibackend.domain.user.entity.Role;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import com.example.runmateaibackend.global.client.ClaudeApiClient;
import com.example.runmateaibackend.global.exception.ConflictException;
import com.example.runmateaibackend.global.exception.ResourceNotFoundException;

import jakarta.persistence.EntityManager;

/**
 * FeedbackService 단위 테스트.
 *
 * AI 피드백 생성과, "AI가 플랜 조정이 필요하다고 판단했을 때 기존 플랜을 비활성화하고
 * 새 플랜을 만드는" 자동 조정 흐름을 검증한다. entityManager는 @PersistenceContext로
 * 필드 주입되는 대상이라 생성자에 없으므로 ReflectionTestUtils로 직접 채워 넣는다.
 */
@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private RecordRepository recordRepository;
	@Mock
	private PlanRepository planRepository;
	@Mock
	private PlanDayProgressRepository planDayProgressRepository;
	@Mock
	private PlanDataMerger planDataMerger;
	@Mock
	private FeedbackRepository feedbackRepository;
	@Mock
	private ClaudeApiClient claudeApiClient;
	@Mock
	private FeedbackPromptBuilder feedbackPromptBuilder;
	@Mock
	private EntityManager entityManager;

	private FeedbackService feedbackService;

	@BeforeEach
	void setUp() {
		feedbackService = new FeedbackService(
			userRepository, recordRepository, planRepository, planDayProgressRepository,
			planDataMerger, feedbackRepository, claudeApiClient, feedbackPromptBuilder
		);
		ReflectionTestUtils.setField(feedbackService, "entityManager", entityManager);
	}

	private User buildUser(Long id, String email) {
		return User.builder()
			.id(id).email(email).password("encoded").name("테스트유저").role(Role.USER)
			.build();
	}

	private TrainingRecord buildRecord(Long id, User user) {
		return TrainingRecord.builder()
			.id(id).user(user).runDate(LocalDate.of(2026, 8, 1))
			.build();
	}

	private TrainingPlan buildActivePlan(User user) {
		return TrainingPlan.builder()
			.id(1L).user(user).planData("{}").goalType("FIVE_K")
			.startDate(LocalDate.of(2026, 7, 1)).isActive(true)
			.build();
	}

	private AiFeedbackResult buildAiResult(String feedback, boolean planUpdateNeeded, Object updatedPlanData) {
		AiFeedbackResult result = new AiFeedbackResult();
		ReflectionTestUtils.setField(result, "feedback", feedback);
		ReflectionTestUtils.setField(result, "planUpdateNeeded", planUpdateNeeded);
		ReflectionTestUtils.setField(result, "updatedPlanData", updatedPlanData);
		return result;
	}

	@Test
	@DisplayName("존재하지 않는 기록으로 피드백을 요청하면 ResourceNotFoundException이 발생한다")
	void createFeedback_recordNotFound_throwsResourceNotFoundException() {
		User user = buildUser(1L, "user@runmateai.com");
		when(userRepository.findByEmail("user@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> feedbackService.createFeedback("user@runmateai.com", 999L))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("기록을 찾을 수 없습니다.");
	}

	@Test
	@DisplayName("이미 해당 기록에 대한 피드백이 존재하면 ConflictException이 발생한다 (중복 요청 방지)")
	void createFeedback_alreadyExists_throwsConflictException() {
		User user = buildUser(2L, "user2@runmateai.com");
		TrainingRecord record = buildRecord(10L, user);
		when(userRepository.findByEmail("user2@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.findById(10L)).thenReturn(Optional.of(record));
		when(feedbackRepository.findByTrainingRecordId(10L))
			.thenReturn(List.of(com.example.runmateaibackend.domain.feedback.entity.AiFeedback.builder().build()));

		assertThatThrownBy(() -> feedbackService.createFeedback("user2@runmateai.com", 10L))
			.isInstanceOf(ConflictException.class)
			.hasMessage("이미 이 기록에 대한 피드백이 존재합니다.");

		// Claude API까지 호출되면 안 된다 (불필요한 비용 발생 방지).
		verify(claudeApiClient, never()).sendMessageAndParse(anyString(), any());
	}

	@Test
	@DisplayName("활성화된 플랜이 없으면 ResourceNotFoundException이 발생한다")
	void createFeedback_noActivePlan_throwsResourceNotFoundException() {
		User user = buildUser(3L, "user3@runmateai.com");
		TrainingRecord record = buildRecord(20L, user);
		when(userRepository.findByEmail("user3@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.findById(20L)).thenReturn(Optional.of(record));
		when(feedbackRepository.findByTrainingRecordId(20L)).thenReturn(List.of());
		when(recordRepository.findTop5ByUserOrderByRunDateDesc(user)).thenReturn(List.of());
		when(planRepository.findFirstByUserAndIsActiveOrderByCreatedAtDesc(user, true))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> feedbackService.createFeedback("user3@runmateai.com", 20L))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("활성화된 플랜이 없습니다.");
	}

	@Test
	@DisplayName("AI가 플랜 조정이 필요없다고 판단하면, 기존 플랜은 그대로 두고 피드백만 저장한다")
	void createFeedback_noPlanUpdateNeeded_onlySavesFeedback() {
		User user = buildUser(4L, "user4@runmateai.com");
		TrainingRecord record = buildRecord(30L, user);
		TrainingPlan activePlan = buildActivePlan(user);

		when(userRepository.findByEmail("user4@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.findById(30L)).thenReturn(Optional.of(record));
		when(feedbackRepository.findByTrainingRecordId(30L)).thenReturn(List.of());
		when(recordRepository.findTop5ByUserOrderByRunDateDesc(user)).thenReturn(List.of(record));
		when(planRepository.findFirstByUserAndIsActiveOrderByCreatedAtDesc(user, true))
			.thenReturn(Optional.of(activePlan));
		when(feedbackPromptBuilder.build(record, List.of(record), activePlan)).thenReturn("prompt");
		when(claudeApiClient.sendMessageAndParse(eq("prompt"), eq(AiFeedbackResult.class)))
			.thenReturn(buildAiResult("좋은 페이스입니다!", false, null));

		FeedbackResponse response = feedbackService.createFeedback("user4@runmateai.com", 30L);

		assertThat(response).isNotNull();
		// 플랜 조정이 필요 없으므로, 새 플랜 저장(교체)이 일어나면 안 된다.
		verify(planRepository, never()).save(any());
		verify(entityManager, never()).flush();
		verify(feedbackRepository).save(any());
	}

	@Test
	@DisplayName("AI가 플랜 조정이 필요하다고 판단하면, 기존 플랜을 비활성화하고 " +
		"새 플랜을 생성하며 진행 기록을 새 플랜으로 이관한다")
	void createFeedback_planUpdateNeeded_deactivatesOldPlanAndCreatesNewOne() {
		User user = buildUser(5L, "user5@runmateai.com");
		TrainingRecord record = buildRecord(40L, user);
		TrainingPlan activePlan = buildActivePlan(user);

		when(userRepository.findByEmail("user5@runmateai.com")).thenReturn(Optional.of(user));
		when(recordRepository.findById(40L)).thenReturn(Optional.of(record));
		when(feedbackRepository.findByTrainingRecordId(40L)).thenReturn(List.of());
		when(recordRepository.findTop5ByUserOrderByRunDateDesc(user)).thenReturn(List.of(record));
		when(planRepository.findFirstByUserAndIsActiveOrderByCreatedAtDesc(user, true))
			.thenReturn(Optional.of(activePlan));
		when(feedbackPromptBuilder.build(record, List.of(record), activePlan)).thenReturn("prompt");
		when(claudeApiClient.sendMessageAndParse(eq("prompt"), eq(AiFeedbackResult.class)))
			.thenReturn(buildAiResult("강도를 낮추겠습니다.", true, java.util.Map.of("week1", "easy")));
		when(planDataMerger.merge(anyString(), anyString(), any(), any())).thenReturn("{\"merged\":true}");
		when(planDayProgressRepository.findByTrainingPlan(activePlan)).thenReturn(List.of());

		feedbackService.createFeedback("user5@runmateai.com", 40L);

		// 1) 기존 플랜은 비활성화되어야 한다.
		assertThat(activePlan.isActive()).isFalse();
		// 2) 비활성화를 즉시 반영하기 위해 flush가 호출되어야 한다 (JPA dirty checking 타이밍 이슈 방지).
		verify(entityManager).flush();
		// 3) 병합된 플랜 데이터로 새 플랜이 저장되어야 한다 (기존 플랜 저장 1회 + 새 플랜 저장 1회 = 총 2회).
		verify(planRepository, org.mockito.Mockito.times(2)).save(any());
		// 4) 새 플랜으로 진행 기록 이관 로직까지 이어져야 한다.
		verify(planDayProgressRepository).findByTrainingPlan(activePlan);
	}

	@Test
	@DisplayName("존재하지 않는 유저로 피드백 목록을 조회하면 ResourceNotFoundException이 발생한다")
	void getFeedbacks_userNotFound_throwsResourceNotFoundException() {
		when(userRepository.findByEmail("ghost@runmateai.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> feedbackService.getFeedbacks("ghost@runmateai.com"))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("유저를 찾을 수 없습니다.");
	}
}