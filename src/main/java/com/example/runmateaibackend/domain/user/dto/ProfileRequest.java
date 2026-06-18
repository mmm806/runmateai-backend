package com.example.runmateaibackend.domain.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ProfileRequest {

	@NotBlank(message = "현재 페이스는 필수입니다.")
	private String currentPace;

	@Min(value = 0, message = "주간 달리기 횟수는 0 이상이어야 합니다.")
	private int weeklyRuns;

	@NotBlank(message = "목표 종류는 필수입니다.")
	private String goalType; // 5k, 10k, half, full

	@Min(value = 1, message = "목표 기간은 1주 이상이어야 합니다.")
	private int targetWeeks;

	@NotBlank(message = "체력 수준은 필수입니다.")
	private String fitnessLevel; // beginner, intermediate, advanced
}
