package com.example.runmateaibackend.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;
import com.example.runmateaibackend.domain.user.repository.UserProfileRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import com.example.runmateaibackend.global.client.ClaudeApiClient;
import com.example.runmateaibackend.support.IntegrationTestSupport;

import jakarta.transaction.Transactional;

/**
 * 동시 요청 시 활성 플랜 경쟁 상태(Race Condition) 재발 방지 테스트.
 *
 * 배경: 동시에 여러 요청이 createPlan()을 호출했을 때, "기존 활성 플랜 확인 → 새 플랜
 * 생성" 사이에 다른 요청이 끼어들며 한 유저에게 활성 플랜이 여러 개 생기던 사고가
 * 있었다(DataIntegrityViolationException, IncorrectResultSizeDataAccessException).
 * 이를 유저 단위 JVM 락(PlanService.userPlanLocks)으로 해결했는데, 이 테스트는
 * 실제로 여러 스레드가 동시에 createPlan()을 호출해도 데이터가 깨지지 않는지
 * H2가 아닌 실제 PostgreSQL(Testcontainers)로 재현/검증한다.
 *
 * 만약 나중에 누군가 실수로 이 락 로직을 제거하거나 망가뜨리면, 이 테스트가
 * 즉시 실패하며 알려준다.
 */
class PlanServiceConcurrencyTest extends IntegrationTestSupport {

	@Autowired
	private PlanService planService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UserProfileRepository userProfileRepository;
	@Autowired
	private PlanRepository planRepository;

	// Claude API 실제 호출은 외부 네트워크가 필요하고 느리므로, 테스트에서는
	// 즉시 가짜 응답을 반환하도록 대체한다. 동시성 자체를 검증하는 것이 목적이지,
	// 실제 AI 응답 내용을 검증하는 테스트가 아니다.
	@MockitoBean
	private ClaudeApiClient claudeApiClient;

	private User testUser;

	@BeforeEach
	void setUp() {
		testUser = userRepository.save(User.builder()
			.email("concurrency-test@runmateai.com")
			.password("dummy-encoded-password")
			.name("동시성테스트유저")
			.build());

		userProfileRepository.save(UserProfile.builder()
			.user(testUser)
			.targetPace("6'00\"")
			.targetWeeklyRuns(3)
			.goalType("FIVE_K")
			.targetWeeks(4)
			.fitnessLevel("BEGINNER")
			.monthlyGoalKm(BigDecimal.valueOf(50))
			.build());

		when(claudeApiClient.sendMessage(any())).thenReturn("{}");
	}

	@Autowired
	private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

	@AfterEach
	void tearDown() {
		// @AfterEach 메서드에 붙인 @Transactional은 Spring 테스트 프레임워크가
		// 인식하지 못한다(테스트 트랜잭션 감지는 @Test 메서드 기준으로만 동작한다).
		// 그래서 TransactionTemplate으로 직접 트랜잭션 경계를 명시한다.
		transactionTemplate.executeWithoutResult(status -> {
			planRepository.deleteByUser(testUser);
			userProfileRepository.findByUser(testUser).ifPresent(userProfileRepository::delete);
			userRepository.delete(testUser);
		});
	}

	@Test
	@DisplayName("같은 유저가 동시에 플랜 생성을 5번 요청해도, 에러 없이 전부 성공하고 " +
		"최종적으로 활성 플랜은 정확히 1개만 남는다")
	void concurrentCreatePlan_resultsInExactlyOneActivePlan() throws InterruptedException {
		int concurrentRequests = 5;
		ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

		List<Callable<Boolean>> tasks = IntStream.range(0, concurrentRequests)
			.<Callable<Boolean>>mapToObj(i -> () -> {
				planService.createPlan(testUser.getEmail());
				return true;
			})
			.toList();

		try {
			List<Future<Boolean>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);

			// 1) 동시 요청 5건이 예외 없이 전부 성공했는지 확인.
			// (오늘 아침 겪었던 사고 재현 시나리오라면 여기서 일부가
			//  DataIntegrityViolationException 등으로 실패했어야 한다.)
			for (Future<Boolean> future : futures) {
				assertThat(future.get()).isTrue();
			}
		} catch (Exception e) {
			throw new RuntimeException("동시 요청 처리 중 예외 발생", e);
		} finally {
			executor.shutdown();
		}

		// 2) 최종적으로 이 유저의 활성 플랜(is_active=true)은 정확히 1개여야 한다.
		List<TrainingPlan> allPlans = planRepository.findByUserOrderByCreatedAtDesc(testUser);
		long activePlanCount = allPlans.stream().filter(TrainingPlan::isActive).count();

		assertThat(allPlans).hasSize(concurrentRequests);
		assertThat(activePlanCount)
			.as("동시에 %d건을 요청해도 활성 플랜은 반드시 1개여야 한다 (one_active_plan_per_user 정합성)",
				concurrentRequests)
			.isEqualTo(1);
	}
}