package com.example.runmateaibackend.domain.user.dto;

import com.example.runmateaibackend.domain.user.entity.UserProfile;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class ProfileResponse {

	private String targetPace;
	private int targetWeeklyRuns;
	private String goalType;
	private int targetWeeks;
	private String fitnessLevel;
	private BigDecimal monthlyGoalKm;

	public ProfileResponse(UserProfile profile) {
		this.targetPace = profile.getTargetPace();
		this.targetWeeklyRuns = profile.getTargetWeeklyRuns();
		this.goalType = profile.getGoalType();
		this.targetWeeks = profile.getTargetWeeks();
		this.fitnessLevel = profile.getFitnessLevel();
		this.monthlyGoalKm = profile.getMonthlyGoalKm();
	}
}