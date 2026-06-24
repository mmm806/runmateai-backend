package com.example.runmateaibackend.domain.record.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@AllArgsConstructor
public class RecordStatsResponse {

	private int totalRuns;
	private BigDecimal totalDistanceKm;
	private int totalDurationMin;
	private String avgPace;

	private BigDecimal longestDistanceKm;
	private String bestPace;
	private int longestDurationMin;

	private int currentStreak;
	private int longestStreak;

	private Map<String, Integer> feelingDistribution;

	private int totalPlanUpdates;

	private BigDecimal thisMonthDistanceKm;
	private BigDecimal lastMonthDistanceKm;
	private Double distanceChangePercent;

	private Integer avgHeartRate;
	private Integer totalCalories;

	private BigDecimal monthlyGoalKm;
	private BigDecimal monthlyGoalProgressPercent;

	private Map<String, BestRecordInfo> bestRecordsByGoalType;

	private Integer totalElevationGain;
}