package com.example.runmateaibackend.domain.record.service;

import com.example.runmateaibackend.domain.feedback.entity.AiFeedback;
import com.example.runmateaibackend.domain.feedback.repository.FeedbackRepository;
import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.plan.service.PlanService;
import com.example.runmateaibackend.domain.record.dto.*;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.record.repository.RecordRepository;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;
import com.example.runmateaibackend.domain.user.repository.UserProfileRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordService {

	private final UserRepository userRepository;
	private final PlanRepository planRepository;
	private final RecordRepository recordRepository;
	private final FeedbackRepository feedbackRepository;
	private final UserProfileRepository userProfileRepository;
	private final PlanService planService;

	@Transactional
	public RecordResponse createRecord(String email, RecordRequest request) {

		User user = findUserByEmail(email);

		if (recordRepository.findByUserAndRunDate(user, request.getRunDate()).isPresent()) {
			throw new IllegalArgumentException("해당 날짜에 이미 기록이 존재합니다.");
		}

		TrainingPlan activePlan = planRepository.findByUserAndIsActive(user, true)
			.orElse(null);

		TrainingRecord record = TrainingRecord.builder()
			.user(user)
			.trainingPlan(activePlan)
			.runDate(request.getRunDate())
			.distanceKm(request.getDistanceKm())
			.durationMin(request.getDurationMin())
			.avgPace(request.getAvgPace())
			.avgHeartRate(request.getAvgHeartRate())
			.calories(request.getCalories())
			.feeling(request.getFeeling())
			.note(request.getNote())
			.elevationGain(request.getElevationGain())
			.build();

		recordRepository.save(record);

		// 그날 플랜의 목표 거리를 채웠는지 자동으로 평가해 완료 처리한다.
		planService.evaluateCompletion(record);

		return new RecordResponse(record);
	}

	public List<RecordResponse> getRecords(String email) {

		User user = findUserByEmail(email);

		return recordRepository.findByUserOrderByRunDateDesc(user)
			.stream()
			.map(RecordResponse::new)
			.toList();
	}

	public RecordResponse getRecordByDate(String email, LocalDate date) {

		User user = findUserByEmail(email);

		TrainingRecord record = recordRepository.findByUserAndRunDate(user, date)
			.orElseThrow(() -> new IllegalArgumentException("해당 날짜에 기록이 없습니다."));

		return new RecordResponse(record);
	}

	@Transactional
	public RecordResponse updateRecord(String email, Long recordId, RecordRequest request) {

		User user = findUserByEmail(email);

		TrainingRecord record = recordRepository.findById(recordId)
			.orElseThrow(() -> new IllegalArgumentException("기록을 찾을 수 없습니다."));

		if (!record.getUser().getId().equals(user.getId())) {
			throw new IllegalArgumentException("본인의 기록만 수정할 수 있습니다.");
		}

		if (!record.getRunDate().equals(request.getRunDate())) {
			recordRepository.findByUserAndRunDate(user, request.getRunDate())
				.ifPresent(existing -> {
					throw new IllegalArgumentException("해당 날짜에 이미 다른 기록이 존재합니다.");
				});
		}

		record.update(
			request.getRunDate(),
			request.getDistanceKm(),
			request.getDurationMin(),
			request.getAvgPace(),
			request.getAvgHeartRate(),
			request.getCalories(),
			request.getFeeling(),
			request.getNote(),
			request.getElevationGain()
		);

		// 거리/날짜가 바뀌었을 수 있으므로 완료 여부를 다시 평가한다.
		planService.evaluateCompletion(record);

		return new RecordResponse(record);
	}

	@Transactional
	public void deleteRecord(String email, Long recordId) {

		User user = findUserByEmail(email);

		TrainingRecord record = recordRepository.findById(recordId)
			.orElseThrow(() -> new IllegalArgumentException("기록을 찾을 수 없습니다."));

		if (!record.getUser().getId().equals(user.getId())) {
			throw new IllegalArgumentException("본인의 기록만 삭제할 수 있습니다.");
		}

		List<AiFeedback> relatedFeedbacks = feedbackRepository.findByTrainingRecordId(recordId);
		boolean wasPlanUpdatedByThisRecord = relatedFeedbacks.stream()
			.anyMatch(AiFeedback::isPlanUpdated);

		// 이 기록으로 인해 평가된 완료 상태가 있다면 먼저 되돌린다 (FK 제약 위반 방지 포함).
		planService.revertCompletionForRecord(record);

		feedbackRepository.deleteByTrainingRecord(record);
		recordRepository.delete(record);

		if (wasPlanUpdatedByThisRecord) {
			revertToLatestPlanUpdate(user);
		}
	}

	private void revertToLatestPlanUpdate(User user) {

		planRepository.findByUserAndIsActive(user, true)
			.ifPresent(TrainingPlan::deactivate);

		List<AiFeedback> feedbacks = feedbackRepository.findByUserOrderByCreatedAtDesc(user);

		AiFeedback latestPlanUpdateFeedback = feedbacks.stream()
			.filter(AiFeedback::isPlanUpdated)
			.findFirst()
			.orElse(null);

		if (latestPlanUpdateFeedback != null) {
			TrainingPlan planToReactivate = latestPlanUpdateFeedback.getTrainingPlan();
			planToReactivate.activate();
		} else {
			planRepository.findFirstByUserOrderByCreatedAtAsc(user)
				.ifPresent(TrainingPlan::activate);
		}
	}

	public RecordStatsResponse getStats(String email) {

		long startTime = System.currentTimeMillis();

		User user = findUserByEmail(email);

		// 최적화: 전체 기록을 메모리에 올리는 대신, DB에서 집계값만 가져옴
		long totalCount = recordRepository.countByUser(user);

		// 기록이 없으면 빈 통계 객체 반환 (예외 대신) — 처음 가입한 유저가 통계 페이지에 접속해도 500 에러가 나지 않게
		if (totalCount == 0) {
			UserProfile profile = userProfileRepository.findByUser(user).orElse(null);
			BigDecimal monthlyGoal = profile != null ? profile.getMonthlyGoalKm() : null;
			return new RecordStatsResponse(
				0,                  // totalRuns
				BigDecimal.ZERO,    // totalDistanceKm
				0,                  // totalDurationMin
				"-",                // avgPace
				BigDecimal.ZERO,    // longestDistanceKm
				"-",                // bestPace
				0,                  // longestDurationMin
				0,                  // currentStreak
				0,                  // longestStreak
				new HashMap<>(),    // feelingDistribution
				0,                  // totalPlanUpdates
				BigDecimal.ZERO,    // thisMonthDistanceKm
				BigDecimal.ZERO,    // lastMonthDistanceKm
				0.0,                // distanceChangePercent
				null,               // avgHeartRate
				null,               // totalCalories
				monthlyGoal,        // monthlyGoalKm
				monthlyGoal != null ? BigDecimal.ZERO : null, // monthlyGoalProgressPercent
				new HashMap<>(),    // bestRecordsByGoalType
				null                // totalElevationGain
			);
		}

		// ① DB 집계 쿼리로 기본 통계 한 번에 계산 (전체 기록을 메모리에 올리지 않음)
		RecordStatsProjection stats = recordRepository.findAggregatedStatsByUser(user);

		int totalRuns = stats.getTotalRuns().intValue();
		BigDecimal totalDistance = stats.getTotalDistance() != null ? stats.getTotalDistance() : BigDecimal.ZERO;
		int totalDuration = stats.getTotalDuration() != null ? stats.getTotalDuration().intValue() : 0;
		BigDecimal longestDistance = stats.getLongestDistance() != null ? stats.getLongestDistance() : BigDecimal.ZERO;
		int longestDuration = stats.getLongestDuration() != null ? stats.getLongestDuration() : 0;
		Integer avgHeartRate = stats.getAvgHeartRate() != null ? stats.getAvgHeartRate().intValue() : null;
		Integer totalCalories = stats.getTotalCalories() != null ? stats.getTotalCalories().intValue() : null;
		Integer totalElevationGain = stats.getTotalElevationGain() != null ? stats.getTotalElevationGain().intValue() : null;

		String avgPace = calculatePace(totalDuration, totalDistance);

		// ② 컨디션 분포 — DB GROUP BY로 집계
		Map<String, Integer> feelingDistribution = new HashMap<>();
		recordRepository.countByFeeling(user).forEach(row -> {
			String feeling = row[0] != null ? (String) row[0] : "unknown";
			int cnt = ((Long) row[1]).intValue();
			feelingDistribution.put(feeling, cnt);
		});

		// ③ 월별 거리 — DB에서 날짜 조건 필터 후 합산
		YearMonth thisMonth = YearMonth.now();
		YearMonth lastMonth = thisMonth.minusMonths(1);
		BigDecimal thisMonthDistance = recordRepository.sumDistanceByUserAndMonth(user, thisMonth.getYear(), thisMonth.getMonthValue());
		BigDecimal lastMonthDistance = recordRepository.sumDistanceByUserAndMonth(user, lastMonth.getYear(), lastMonth.getMonthValue());

		Double distanceChangePercent = null;
		if (lastMonthDistance.compareTo(BigDecimal.ZERO) > 0) {
			distanceChangePercent = thisMonthDistance.subtract(lastMonthDistance)
				.divide(lastMonthDistance, 4, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.doubleValue();
		}

		// ④ 스트릭 계산 — 날짜 컬럼만 조회 (전체 엔티티 불필요)
		List<LocalDate> dates = recordRepository.findRunDatesByUser(user);
		int[] streaks = calculateStreaksByDates(dates);
		int currentStreak = streaks[0];
		int longestStreak = streaks[1];

		// ⑤ 최고 페이스 — 전체 기록을 로드하지 않고, DB에서 정렬 후 1건만 조회
		String bestPace = recordRepository.findBestPaceRecordByUserId(user.getId())
			.map(r -> calculatePace(r.getDurationMin(), r.getDistanceKm()))
			.orElse("-");

		// ⑥ 플랜 업데이트 횟수 — 전체 피드백을 로드하지 않고 COUNT 쿼리로 처리
		int totalPlanUpdates = (int) feedbackRepository.countByUserAndPlanUpdatedTrue(user);

		// ⑦ 목표별 베스트 기록 — 목표 구간(5k/10k/half/full)마다 조건에 맞는 1건만 조회
		Map<String, BestRecordInfo> bestRecordsByGoalType = calculateBestRecordsByGoalType(user);

		UserProfile profile = userProfileRepository.findByUser(user).orElse(null);
		BigDecimal monthlyGoalKm = profile != null ? profile.getMonthlyGoalKm() : null;
		BigDecimal monthlyGoalProgress = null;
		if (monthlyGoalKm != null && monthlyGoalKm.compareTo(BigDecimal.ZERO) > 0) {
			monthlyGoalProgress = thisMonthDistance
				.divide(monthlyGoalKm, 4, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100));
		}

		long endTime = System.currentTimeMillis();
		log.info("[STATS PERFORMANCE] 기록 {}개 처리 시간: {}ms (최적화 후)", totalRuns, endTime - startTime);

		return new RecordStatsResponse(
			totalRuns, totalDistance, totalDuration, avgPace,
			longestDistance, bestPace, longestDuration,
			currentStreak, longestStreak,
			feelingDistribution, totalPlanUpdates,
			thisMonthDistance, lastMonthDistance, distanceChangePercent,
			avgHeartRate, totalCalories,
			monthlyGoalKm, monthlyGoalProgress,
			bestRecordsByGoalType,
			totalElevationGain
		);
	}

	// 최적화: 유저의 전체 기록을 로드하는 대신, 목표 구간별로 조건에 맞는 1건만 DB에서 조회
	private Map<String, BestRecordInfo> calculateBestRecordsByGoalType(User user) {

		Map<String, BigDecimal> categoryRanges = Map.of(
			"5k", BigDecimal.valueOf(5),
			"10k", BigDecimal.valueOf(10),
			"half", BigDecimal.valueOf(21.1),
			"full", BigDecimal.valueOf(42.2)
		);

		Map<String, BestRecordInfo> result = new HashMap<>();

		for (Map.Entry<String, BigDecimal> entry : categoryRanges.entrySet()) {
			String category = entry.getKey();
			BigDecimal targetDistance = entry.getValue();

			BigDecimal lowerBound = targetDistance.multiply(BigDecimal.valueOf(0.9));
			BigDecimal upperBound = targetDistance.multiply(BigDecimal.valueOf(1.1));

			recordRepository.findFirstByUserAndDistanceKmBetweenOrderByDurationMinAsc(user, lowerBound, upperBound)
				.ifPresent(best -> result.put(category, new BestRecordInfo(
					best.getDistanceKm(),
					calculatePace(best.getDurationMin(), best.getDistanceKm()),
					best.getDurationMin(),
					best.getRunDate()
				)));
		}

		return result;
	}

	private String calculatePace(int durationMin, BigDecimal distanceKm) {
		if (distanceKm.compareTo(BigDecimal.ZERO) == 0) return "-";
		double paceMinPerKm = durationMin / distanceKm.doubleValue();
		int minutes = (int) paceMinPerKm;
		int seconds = (int) Math.round((paceMinPerKm - minutes) * 60);
		return String.format("%d'%02d\"", minutes, seconds);
	}

	// 최적화: 날짜 리스트만 받아서 스트릭 계산 (전체 엔티티 불필요)
	private int[] calculateStreaksByDates(List<LocalDate> dates) {

		if (dates.isEmpty()) return new int[]{0, 0};

		int currentStreak = 1;
		int longestStreak = 1;
		int tempStreak = 1;

		LocalDate today = LocalDate.now();
		if (!dates.get(0).equals(today) && !dates.get(0).equals(today.minusDays(1))) {
			currentStreak = 0;
		}

		for (int i = 0; i < dates.size() - 1; i++) {
			long diff = ChronoUnit.DAYS.between(dates.get(i + 1), dates.get(i));
			if (diff == 1) {
				tempStreak++;
				if (currentStreak != 0 && i + 1 < currentStreak + 1) {
					currentStreak = tempStreak;
				}
			} else {
				longestStreak = Math.max(longestStreak, tempStreak);
				tempStreak = 1;
			}
		}
		longestStreak = Math.max(longestStreak, tempStreak);

		return new int[]{currentStreak, longestStreak};
	}

	private User findUserByEmail(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
	}
}