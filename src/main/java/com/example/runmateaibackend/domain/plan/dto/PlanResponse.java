package com.example.runmateaibackend.domain.plan.dto;

import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import lombok.Getter;

@Getter
public class PlanResponse {

	private Long id;
	private String goalType;
	private String planData; // JSON 문자열 그대로 전달

	public PlanResponse(TrainingPlan plan) {
		this.id = plan.getId();
		this.goalType = plan.getGoalType();
		this.planData = plan.getPlanData();
	}
}