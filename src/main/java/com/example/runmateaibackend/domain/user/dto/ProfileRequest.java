package com.example.runmateaibackend.domain.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class ProfileRequest {

	@NotBlank(message = "목표 페이스는 필수입니다.")
	private String targetPace;

	@Min(value = 0, message = "목표 주간 달리기 횟수는 0 이상이어야 합니다.")
	private int targetWeeklyRuns;

	@NotBlank(message = "목표 종류는 필수입니다.")
	private String goalType;

	@Min(value = 1, message = "목표 기간은 1주 이상이어야 합니다.")
	private int targetWeeks;

	@NotBlank(message = "체력 수준은 필수입니다.")
	private String fitnessLevel;

	private BigDecimal monthlyGoalKm; // 선택 입력
}