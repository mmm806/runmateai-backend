package com.example.runmateaibackend.domain.record.entity;

import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "training_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class TrainingRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plan_id")
	private TrainingPlan trainingPlan;

	@Column(name = "run_date", nullable = false)
	private LocalDate runDate;

	@Column(name = "distance_km", nullable = false, precision = 5, scale = 2)
	private BigDecimal distanceKm;

	@Column(name = "duration_min", nullable = false)
	private int durationMin;

	@Column(name = "avg_pace", nullable = false, length = 10)
	private String avgPace;

	@Column(name = "avg_heart_rate")
	private Integer avgHeartRate;

	@Column(name = "calories")
	private Integer calories;

	@Column(name = "feeling", length = 20)
	private String feeling;

	@Column(name = "note", length = 500)
	private String note;

	@Column(name = "elevation_gain")
	private Integer elevationGain; // 미터(m) 단위, 선택 입력

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	public void update(LocalDate runDate, BigDecimal distanceKm, int durationMin,
		String avgPace, Integer avgHeartRate, Integer calories,
		String feeling, String note, Integer elevationGain) {
		this.runDate = runDate;
		this.distanceKm = distanceKm;
		this.durationMin = durationMin;
		this.avgPace = avgPace;
		this.avgHeartRate = avgHeartRate;
		this.calories = calories;
		this.feeling = feeling;
		this.note = note;
		this.elevationGain = elevationGain;
	}
}