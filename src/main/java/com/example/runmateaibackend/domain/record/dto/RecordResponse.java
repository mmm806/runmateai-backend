package com.example.runmateaibackend.domain.record.dto;

import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
public class RecordResponse {

	private Long id;
	private LocalDate runDate;
	private BigDecimal distanceKm;
	private int durationMin;
	private String avgPace;
	private Integer avgHeartRate;
	private Integer calories;
	private String feeling;
	private String note;
	private Integer elevationGain;

	public RecordResponse(TrainingRecord record) {
		this.id = record.getId();
		this.runDate = record.getRunDate();
		this.distanceKm = record.getDistanceKm();
		this.durationMin = record.getDurationMin();
		this.avgPace = record.getAvgPace();
		this.avgHeartRate = record.getAvgHeartRate();
		this.calories = record.getCalories();
		this.feeling = record.getFeeling();
		this.note = record.getNote();
		this.elevationGain = record.getElevationGain();
	}
}