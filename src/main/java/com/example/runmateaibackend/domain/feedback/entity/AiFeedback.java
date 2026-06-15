package com.example.runmateaibackend.domain.feedback.entity;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.user.entity.User;

@Entity
@Table(name = "ai_feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AiFeedback {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "record_id", nullable = false)
	private TrainingRecord trainingRecord;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plan_id", nullable = false)
	private TrainingPlan trainingPlan;

	@Column(name = "feedback_content", nullable = false, columnDefinition = "TEXT")
	private String feedbackContent;

	@Column(name = "plan_updated", nullable = false)
	@Builder.Default
	private boolean planUpdated = false;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}
}
