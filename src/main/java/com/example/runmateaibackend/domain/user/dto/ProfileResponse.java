package com.example.runmateaibackend.domain.user.dto;

import com.example.runmateaibackend.domain.user.entity.UserProfile;

import lombok.Getter;

@Getter
public class ProfileResponse {

	private String currentPace;
	private int weeklyRuns;
	private String goalType;
	private int targetWeeks;
	private String fitnessLevel;

	public ProfileResponse(UserProfile profile) {
		this.currentPace = profile.getCurrentPace();
		this.weeklyRuns = profile.getWeeklyRuns();
		this.goalType = profile.getGoalType();
		this.targetWeeks = profile.getTargetWeeks();
		this.fitnessLevel = profile.getFitnessLevel();
	}
}
