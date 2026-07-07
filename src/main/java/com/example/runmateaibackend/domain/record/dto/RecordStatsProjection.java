package com.example.runmateaibackend.domain.record.dto;

import java.math.BigDecimal;

/**
 * getStats()의 DB 집계 쿼리 결과를 받기 위한 Projection 인터페이스.
 * Spring Data JPA가 쿼리 결과를 이 인터페이스의 구현체로 자동 매핑해준다.
 * 전체 엔티티(TrainingRecord)를 메모리에 올리지 않고 집계 결과값만 받아오는 것이 핵심.
 */
public interface RecordStatsProjection {

	Long getTotalRuns();
	BigDecimal getTotalDistance();
	Long getTotalDuration();
	BigDecimal getLongestDistance();
	Double getAvgHeartRate();
	Long getTotalCalories();
	Long getTotalElevationGain();
}