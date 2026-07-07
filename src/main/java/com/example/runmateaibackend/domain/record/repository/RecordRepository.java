package com.example.runmateaibackend.domain.record.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.runmateaibackend.domain.record.dto.RecordStatsProjection;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.user.entity.User;

public interface RecordRepository extends JpaRepository<TrainingRecord, Long> {

	// 유저의 전체 기록 최신순 조회
	List<TrainingRecord> findByUserOrderByRunDateDesc(User user);

	// 특정 날짜 기록 조회 (날짜 중복 방지)
	Optional<TrainingRecord> findByUserAndRunDate(User user, LocalDate runDate);

	// 유저의 최근 N개 기록 조회
	List<TrainingRecord> findTop5ByUserOrderByRunDateDesc(User user);

	// 유저 조회후 기록 삭제
	void deleteByUser(User user);

	List<TrainingRecord> findByUser(User user);

	long countByUser(User user);

	/**
	 * DB 집계 쿼리로 통계 기본값을 한 번에 계산.
	 * 전체 기록을 메모리로 올리는 대신 DB가 직접 집계하고 결과만 반환.
	 * SUM, COUNT, MAX, AVG 등은 DB 인덱스를 활용해 효율적으로 처리됨.
	 */
	@Query("""
		SELECT
		    COUNT(r)            AS totalRuns,
		    SUM(r.distanceKm)   AS totalDistance,
		    SUM(r.durationMin)  AS totalDuration,
		    MAX(r.distanceKm)   AS longestDistance,
		    MAX(r.durationMin)  AS longestDuration,
		    AVG(r.avgHeartRate) AS avgHeartRate,
		    SUM(r.calories)     AS totalCalories,
		    SUM(r.elevationGain) AS totalElevationGain
		FROM TrainingRecord r
		WHERE r.user = :user
		""")
	RecordStatsProjection findAggregatedStatsByUser(@Param("user") User user);

	/**
	 * 최고 페이스(분/km가 가장 낮은) 기록 1건만 조회.
	 * 전체 기록을 로드해서 Java에서 min()을 구하는 대신, DB가 정렬 후 1건만 반환.
	 * duration_min(분) / distance_km(km) = 분/km 페이스이므로 오름차순 정렬 시 첫 번째가 최고 페이스.
	 */
	@Query(
		value = "SELECT * FROM training_records WHERE user_id = :userId ORDER BY (duration_min / distance_km) ASC LIMIT 1",
		nativeQuery = true
	)
	Optional<TrainingRecord> findBestPaceRecordByUserId(@Param("userId") Long userId);

	/**
	 * 목표 거리 구간(예: 5km ±10%) 내에서 가장 빠른(시간이 짧은) 기록 1건 조회.
	 * 목표 타입(5k/10k/half/full)별로 호출되며, 전체 기록 대신 조건에 맞는 1건만 가져옴.
	 */
	Optional<TrainingRecord> findFirstByUserAndDistanceKmBetweenOrderByDurationMinAsc(
		User user, BigDecimal lowerBound, BigDecimal upperBound
	);

	/**
	 * 이번 달 / 지난달 누적 거리를 DB에서 직접 집계.
	 * Java 스트림으로 전체 기록을 필터링하는 대신, DB가 날짜 조건으로 필터 후 합산.
	 */
	@Query("""
		SELECT COALESCE(SUM(r.distanceKm), 0)
		FROM TrainingRecord r
		WHERE r.user = :user
		  AND EXTRACT(YEAR FROM r.runDate) = :year
		  AND EXTRACT(MONTH FROM r.runDate) = :month
		""")
	BigDecimal sumDistanceByUserAndMonth(
		@Param("user") User user,
		@Param("year") int year,
		@Param("month") int month
	);

	/**
	 * 컨디션별 기록 수를 DB에서 집계.
	 */
	@Query("""
		SELECT r.feeling AS feeling, COUNT(r) AS cnt
		FROM TrainingRecord r
		WHERE r.user = :user
		GROUP BY r.feeling
		""")
	List<Object[]> countByFeeling(@Param("user") User user);

	/**
	 * 운동 날짜 목록만 조회 (스트릭 계산용).
	 * 전체 엔티티 대신 날짜 컬럼만 가져와서 네트워크 전송량 최소화.
	 */
	@Query("""
		SELECT r.runDate
		FROM TrainingRecord r
		WHERE r.user = :user
		ORDER BY r.runDate DESC
		""")
	List<LocalDate> findRunDatesByUser(@Param("user") User user);
}