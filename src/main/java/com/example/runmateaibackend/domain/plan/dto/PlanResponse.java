package com.example.runmateaibackend.domain.plan.dto;

import java.time.LocalDate;
import java.util.List;

import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import lombok.Getter;

@Getter
public class PlanResponse {

	private Long id;
	private String goalType;
	private String planData; // JSON 문자열 그대로 전달
	private LocalDate startDate; // 1주차 1일째에 해당하는 실제 날짜
	private List<PlanDayProgressResponse> progress; // 일자별 완료 현황 (없으면 빈 리스트)

	public PlanResponse(TrainingPlan plan) {
		this(plan, List.of());
	}

	public PlanResponse(TrainingPlan plan, List<PlanDayProgressResponse> progress) {
		this.id = plan.getId();
		this.goalType = plan.getGoalType();
		this.planData = plan.getPlanData();
		this.startDate = plan.getStartDate();
		this.progress = progress;
	}
}