package com.example.runmateaibackend.domain.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UserProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "current_pace", nullable = false, length = 10)
	private String currentPace;

	@Column(name = "weekly_runs", nullable = false)
	private int weeklyRuns;

	@Column(name = "goal_type", nullable = false, length = 20)
	private String goalType;

	@Column(name = "target_weeks", nullable = false)
	private int targetWeeks;

	@Column(name = "fitness_level", nullable = false, length = 20)
	private String fitnessLevel;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	public void update(String currentPace, int weeklyRuns, String goalType, int targetWeeks, String fitnessLevel) {
		this.currentPace = currentPace;
		this.weeklyRuns = weeklyRuns;
		this.goalType = goalType;
		this.targetWeeks = targetWeeks;
		this.fitnessLevel = fitnessLevel;
	}
}
