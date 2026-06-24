package com.example.runmateaibackend.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class UserProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "target_pace", nullable = false, length = 10)
	private String targetPace;

	@Column(name = "target_weekly_runs", nullable = false)
	private int targetWeeklyRuns;

	@Column(name = "goal_type", nullable = false, length = 20)
	private String goalType;

	@Column(name = "target_weeks", nullable = false)
	private int targetWeeks;

	@Column(name = "fitness_level", nullable = false, length = 20)
	private String fitnessLevel;

	@Column(name = "monthly_goal_km", precision = 5, scale = 2)
	private BigDecimal monthlyGoalKm; // 선택 입력, null 가능

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	public void update(String targetPace, int targetWeeklyRuns, String goalType,
		int targetWeeks, String fitnessLevel, BigDecimal monthlyGoalKm) {
		this.targetPace = targetPace;
		this.targetWeeklyRuns = targetWeeklyRuns;
		this.goalType = goalType;
		this.targetWeeks = targetWeeks;
		this.fitnessLevel = fitnessLevel;
		this.monthlyGoalKm = monthlyGoalKm;
	}
}