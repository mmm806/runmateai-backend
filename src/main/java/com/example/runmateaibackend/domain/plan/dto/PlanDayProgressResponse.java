package com.example.runmateaibackend.domain.plan.dto;

import java.time.LocalDateTime;

import com.example.runmateaibackend.domain.plan.entity.PlanDayProgress;

import lombok.Getter;

@Getter
public class PlanDayProgressResponse {

	private final int week;
	private final int day;
	private final boolean completed;
	private final Long recordId;
	private final LocalDateTime completedAt;

	public PlanDayProgressResponse(PlanDayProgress progress) {
		this.week = progress.getWeekNumber();
		this.day = progress.getDayNumber();
		this.completed = progress.isCompleted();
		this.recordId = progress.getTriggeringRecord() != null
			? progress.getTriggeringRecord().getId()
			: null;
		this.completedAt = progress.getCompletedAt();
	}
}