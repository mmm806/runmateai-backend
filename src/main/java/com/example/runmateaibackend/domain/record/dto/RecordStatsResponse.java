package com.example.runmateaibackend.domain.record.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@AllArgsConstructor
public class RecordStatsResponse {

	private int totalRuns;              // 총 러닝 횟수
	private BigDecimal totalDistanceKm; // 총 누적 거리
	private int totalDurationMin;       // 총 누적 시간 (분)
	private String avgPace;             // 평균 페이스

	private BigDecimal longestDistanceKm; // 최장 거리
	private String bestPace;              // 최고 페이스
	private int longestDurationMin;       // 최장 시간

	private int currentStreak;  // 현재 연속 기록일
	private int longestStreak;  // 최장 연속 기록일

	private Map<String, Integer> feelingDistribution; // 컨디션 분포 {great: 3, good: 5, ...}

	private int totalPlanUpdates; // AI가 플랜을 조정해준 횟수
}