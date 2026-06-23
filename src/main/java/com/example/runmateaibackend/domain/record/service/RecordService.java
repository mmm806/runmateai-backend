package com.example.runmateaibackend.domain.record.service;

import com.example.runmateaibackend.domain.feedback.entity.AiFeedback;
import com.example.runmateaibackend.domain.feedback.repository.FeedbackRepository;
import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.record.dto.RecordRequest;
import com.example.runmateaibackend.domain.record.dto.RecordResponse;
import com.example.runmateaibackend.domain.record.dto.RecordStatsResponse;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.record.repository.RecordRepository;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecordService {

	private final UserRepository userRepository;
	private final PlanRepository planRepository;
	private final RecordRepository recordRepository;
	private final FeedbackRepository feedbackRepository;

	// 러닝 기록 등록
	@Transactional
	public RecordResponse createRecord(String email, RecordRequest request) {

		User user = findUserByEmail(email);

		// 같은 날짜에 이미 기록이 있는지 확인
		if (recordRepository.findByUserAndRunDate(user, request.getRunDate()).isPresent()) {
			throw new IllegalArgumentException("해당 날짜에 이미 기록이 존재합니다.");
		}

		// 현재 활성 플랜 연결 (없으면 null)
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
			.build();

		recordRepository.save(record);

		return new RecordResponse(record);
	}

	// 전체 기록 조회 (최신순)
	public List<RecordResponse> getRecords(String email) {

		User user = findUserByEmail(email);

		return recordRepository.findByUserOrderByRunDateDesc(user)
			.stream()
			.map(RecordResponse::new)
			.toList();
	}

	// 특정 날짜 기록 조회
	public RecordResponse getRecordByDate(String email, LocalDate date) {

		User user = findUserByEmail(email);

		TrainingRecord record = recordRepository.findByUserAndRunDate(user, date)
			.orElseThrow(() -> new IllegalArgumentException("해당 날짜에 기록이 없습니다."));

		return new RecordResponse(record);
	}

	// 기록 수정
	@Transactional
	public RecordResponse updateRecord(String email, Long recordId, RecordRequest request) {

		User user = findUserByEmail(email);

		TrainingRecord record = recordRepository.findById(recordId)
			.orElseThrow(() -> new IllegalArgumentException("기록을 찾을 수 없습니다."));

		// 본인 기록인지 확인 (다른 유저 기록을 수정하지 못하게)
		if (!record.getUser().getId().equals(user.getId())) {
			throw new IllegalArgumentException("본인의 기록만 수정할 수 있습니다.");
		}

		// 날짜를 변경하는 경우, 변경하려는 날짜에 이미 다른 기록이 있는지 확인
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
			request.getNote()
		);

		return new RecordResponse(record);
	}

	// 기록 삭제
	@Transactional
	public void deleteRecord(String email, Long recordId) {

		User user = findUserByEmail(email);

		TrainingRecord record = recordRepository.findById(recordId)
			.orElseThrow(() -> new IllegalArgumentException("기록을 찾을 수 없습니다."));

		if (!record.getUser().getId().equals(user.getId())) {
			throw new IllegalArgumentException("본인의 기록만 삭제할 수 있습니다.");
		}

		// 이 기록에 연결된 피드백 조회 (보통 1개)
		List<AiFeedback> relatedFeedbacks = feedbackRepository.findByTrainingRecordId(recordId);

		boolean wasPlanUpdatedByThisRecord = relatedFeedbacks.stream()
			.anyMatch(AiFeedback::isPlanUpdated);

		// 연관 피드백 삭제
		feedbackRepository.deleteByTrainingRecord(record);

		// 기록 삭제
		recordRepository.delete(record);

		// 이 기록이 플랜을 조정했었다면, 되돌리기 로직 수행
		if (wasPlanUpdatedByThisRecord) {
			revertToLatestPlanUpdate(user);
		}
	}

	private void revertToLatestPlanUpdate(User user) {

		// 현재 활성 플랜 비활성화
		planRepository.findByUserAndIsActive(user, true)
			.ifPresent(TrainingPlan::deactivate);

		// 남은 피드백 중 가장 최근 것부터 확인, planUpdated=true인 걸 찾을 때까지
		List<AiFeedback> feedbacks = feedbackRepository.findByUserOrderByCreatedAtDesc(user);

		AiFeedback latestPlanUpdateFeedback = feedbacks.stream()
			.filter(AiFeedback::isPlanUpdated)
			.findFirst()
			.orElse(null);

		if (latestPlanUpdateFeedback != null) {
			// 그 피드백이 만들었던 플랜을 다시 활성화
			TrainingPlan planToReactivate = latestPlanUpdateFeedback.getTrainingPlan();
			planToReactivate.activate();
		} else {
			// 조정 이력이 전혀 없으면, 가장 처음 만들어진 플랜을 활성화
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

		// 총 횟수
		int totalRuns = records.size();

		// 총 누적 거리
		BigDecimal totalDistance = records.stream()
			.map(TrainingRecord::getDistanceKm)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		// 총 누적 시간
		int totalDuration = records.stream()
			.mapToInt(TrainingRecord::getDurationMin)
			.sum();

		// 평균 페이스 (전체 시간 / 전체 거리로 재계산)
		String avgPace = calculatePace(totalDuration, totalDistance);

		// 최장 거리
		BigDecimal longestDistance = records.stream()
			.map(TrainingRecord::getDistanceKm)
			.max(BigDecimal::compareTo)
			.orElse(BigDecimal.ZERO);

		// 최고 페이스 (분/km가 가장 작은 것 = 가장 빠른 것)
		String bestPace = records.stream()
			.min((r1, r2) -> Double.compare(
				paceToMinutesPerKm(r1.getDurationMin(), r1.getDistanceKm()),
				paceToMinutesPerKm(r2.getDurationMin(), r2.getDistanceKm())
			))
			.map(r -> calculatePace(r.getDurationMin(), r.getDistanceKm()))
			.orElse("-");

		// 최장 시간
		int longestDuration = records.stream()
			.mapToInt(TrainingRecord::getDurationMin)
			.max()
			.orElse(0);

		// 연속 기록일 계산
		int[] streaks = calculateStreaks(records);
		int currentStreak = streaks[0];
		int longestStreak = streaks[1];

		// 컨디션 분포
		Map<String, Integer> feelingDistribution = new HashMap<>();
		for (TrainingRecord record : records) {
			String feeling = record.getFeeling() != null ? record.getFeeling() : "unknown";
			feelingDistribution.merge(feeling, 1, Integer::sum);
		}

		// AI 플랜 조정 횟수
		int totalPlanUpdates = (int) feedbackRepository.findByUserOrderByCreatedAtDesc(user)
			.stream()
			.filter(AiFeedback::isPlanUpdated)
			.count();

		return new RecordStatsResponse(
			totalRuns, totalDistance, totalDuration, avgPace,
			longestDistance, bestPace, longestDuration,
			currentStreak, longestStreak,
			feelingDistribution, totalPlanUpdates
		);
	}

	// 분/km 계산 (정렬용 숫자값)
	private double paceToMinutesPerKm(int durationMin, BigDecimal distanceKm) {
		if (distanceKm.compareTo(BigDecimal.ZERO) == 0) return Double.MAX_VALUE;
		return durationMin / distanceKm.doubleValue();
	}

	// "6'30\"" 형식으로 페이스 문자열 생성
	private String calculatePace(int durationMin, BigDecimal distanceKm) {
		if (distanceKm.compareTo(BigDecimal.ZERO) == 0) return "-";
		double paceMinPerKm = durationMin / distanceKm.doubleValue();
		int minutes = (int) paceMinPerKm;
		int seconds = (int) Math.round((paceMinPerKm - minutes) * 60);
		return String.format("%d'%02d\"", minutes, seconds);
	}

	// 연속 기록일 계산 (현재 스트릭, 최장 스트릭)
	private int[] calculateStreaks(List<TrainingRecord> records) {

		List<LocalDate> dates = records.stream()
			.map(TrainingRecord::getRunDate)
			.distinct()
			.sorted(Comparator.reverseOrder()) // 최신순
			.toList();

		if (dates.isEmpty()) return new int[]{0, 0};

		int currentStreak = 1;
		int longestStreak = 1;
		int tempStreak = 1;

		// 현재 스트릭: 가장 최근 기록이 오늘 또는 어제인지부터 확인
		LocalDate today = LocalDate.now();
		if (!dates.get(0).equals(today) && !dates.get(0).equals(today.minusDays(1))) {
			currentStreak = 0; // 오늘/어제 기록이 없으면 현재 스트릭은 끊긴 상태
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

		if (currentStreak == 0) currentStreak = 0;

		return new int[]{currentStreak, longestStreak};
	}

	private User findUserByEmail(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
	}
}