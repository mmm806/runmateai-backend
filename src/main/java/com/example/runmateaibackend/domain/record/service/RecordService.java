package com.example.runmateaibackend.domain.record.service;

import com.example.runmateaibackend.domain.feedback.entity.AiFeedback;
import com.example.runmateaibackend.domain.feedback.repository.FeedbackRepository;
import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.record.dto.*;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.record.repository.RecordRepository;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;
import com.example.runmateaibackend.domain.user.repository.UserProfileRepository;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecordService {

	private final UserRepository userRepository;
	private final PlanRepository planRepository;
	private final RecordRepository recordRepository;
	private final FeedbackRepository feedbackRepository;
	private final UserProfileRepository userProfileRepository;

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

		User user = findUserByEmail(email);

		List<TrainingRecord> records = recordRepository.findByUserOrderByRunDateDesc(user);

		if (records.isEmpty()) {
			throw new IllegalArgumentException("러닝 기록이 없습니다.");
		}

		int totalRuns = records.size();

		BigDecimal totalDistance = records.stream()
			.map(TrainingRecord::getDistanceKm)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		int totalDuration = records.stream()
			.mapToInt(TrainingRecord::getDurationMin)
			.sum();

		String avgPace = calculatePace(totalDuration, totalDistance);

		BigDecimal longestDistance = records.stream()
			.map(TrainingRecord::getDistanceKm)
			.max(BigDecimal::compareTo)
			.orElse(BigDecimal.ZERO);

		String bestPace = records.stream()
			.min((r1, r2) -> Double.compare(
				paceToMinutesPerKm(r1.getDurationMin(), r1.getDistanceKm()),
				paceToMinutesPerKm(r2.getDurationMin(), r2.getDistanceKm())
			))
			.map(r -> calculatePace(r.getDurationMin(), r.getDistanceKm()))
			.orElse("-");

		int longestDuration = records.stream()
			.mapToInt(TrainingRecord::getDurationMin)
			.max()
			.orElse(0);

		int[] streaks = calculateStreaks(records);
		int currentStreak = streaks[0];
		int longestStreak = streaks[1];

		Map<String, Integer> feelingDistribution = new HashMap<>();
		for (TrainingRecord record : records) {
			String feeling = record.getFeeling() != null ? record.getFeeling() : "unknown";
			feelingDistribution.merge(feeling, 1, Integer::sum);
		}

		int totalPlanUpdates = (int) feedbackRepository.findByUserOrderByCreatedAtDesc(user)
			.stream()
			.filter(AiFeedback::isPlanUpdated)
			.count();

		// --- 새로 추가된 통계 ---

		YearMonth thisMonth = YearMonth.now();
		YearMonth lastMonth = thisMonth.minusMonths(1);

		BigDecimal thisMonthDistance = sumDistanceForMonth(records, thisMonth);
		BigDecimal lastMonthDistance = sumDistanceForMonth(records, lastMonth);

		Double distanceChangePercent = null;
		if (lastMonthDistance.compareTo(BigDecimal.ZERO) > 0) {
			distanceChangePercent = thisMonthDistance.subtract(lastMonthDistance)
				.divide(lastMonthDistance, 4, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.doubleValue();
		}

		List<Integer> heartRates = records.stream()
			.map(TrainingRecord::getAvgHeartRate)
			.filter(Objects::nonNull)
			.toList();
		Integer avgHeartRate = heartRates.isEmpty() ? null :
			(int) heartRates.stream().mapToInt(Integer::intValue).average().orElse(0);

		List<Integer> caloriesList = records.stream()
			.map(TrainingRecord::getCalories)
			.filter(Objects::nonNull)
			.toList();
		Integer totalCalories = caloriesList.isEmpty() ? null :
			caloriesList.stream().mapToInt(Integer::intValue).sum();

		List<Integer> elevations = records.stream()
			.map(TrainingRecord::getElevationGain)
			.filter(Objects::nonNull)
			.toList();
		Integer totalElevationGain = elevations.isEmpty() ? null :
			elevations.stream().mapToInt(Integer::intValue).sum();

		UserProfile profile = userProfileRepository.findByUser(user).orElse(null);
		BigDecimal monthlyGoalKm = profile != null ? profile.getMonthlyGoalKm() : null;
		BigDecimal monthlyGoalProgress = null;
		if (monthlyGoalKm != null && monthlyGoalKm.compareTo(BigDecimal.ZERO) > 0) {
			monthlyGoalProgress = thisMonthDistance
				.divide(monthlyGoalKm, 4, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100));
		}

		Map<String, BestRecordInfo> bestRecordsByGoalType = calculateBestRecordsByGoalType(records);

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

	private BigDecimal sumDistanceForMonth(List<TrainingRecord> records, YearMonth month) {
		return records.stream()
			.filter(r -> YearMonth.from(r.getRunDate()).equals(month))
			.map(TrainingRecord::getDistanceKm)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private Map<String, BestRecordInfo> calculateBestRecordsByGoalType(List<TrainingRecord> records) {

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

			TrainingRecord best = records.stream()
				.filter(r -> r.getDistanceKm().compareTo(lowerBound) >= 0
					&& r.getDistanceKm().compareTo(upperBound) <= 0)
				.min((r1, r2) -> Integer.compare(r1.getDurationMin(), r2.getDurationMin()))
				.orElse(null);

			if (best != null) {
				result.put(category, new BestRecordInfo(
					best.getDistanceKm(),
					calculatePace(best.getDurationMin(), best.getDistanceKm()),
					best.getDurationMin(),
					best.getRunDate()
				));
			}
		}

		return result;
	}

	private double paceToMinutesPerKm(int durationMin, BigDecimal distanceKm) {
		if (distanceKm.compareTo(BigDecimal.ZERO) == 0) return Double.MAX_VALUE;
		return durationMin / distanceKm.doubleValue();
	}

	private String calculatePace(int durationMin, BigDecimal distanceKm) {
		if (distanceKm.compareTo(BigDecimal.ZERO) == 0) return "-";
		double paceMinPerKm = durationMin / distanceKm.doubleValue();
		int minutes = (int) paceMinPerKm;
		int seconds = (int) Math.round((paceMinPerKm - minutes) * 60);
		return String.format("%d'%02d\"", minutes, seconds);
	}

	private int[] calculateStreaks(List<TrainingRecord> records) {

		List<LocalDate> dates = records.stream()
			.map(TrainingRecord::getRunDate)
			.distinct()
			.sorted(Comparator.reverseOrder())
			.toList();

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