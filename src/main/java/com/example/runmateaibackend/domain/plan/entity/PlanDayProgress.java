package com.example.runmateaibackend.domain.plan.entity;

import java.time.LocalDateTime;

import com.example.runmateaibackend.domain.record.entity.TrainingRecord;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플랜의 특정 (주차, 일자)가 완료되었는지를 추적하는 엔티티.
 * planData(JSON)는 AI가 만든 원본 플랜 그대로 보존하고,
 * 진행 상태는 이 테이블에서 별도로 관리한다.
 * AI 피드백으로 플랜이 갱신되어 새 TrainingPlan이 만들어지면,
 * 옛 플랜에 쌓여있던 완료 기록은 FeedbackService.carryOverProgress()를 통해
 * 새 플랜으로 복사되어 이어진다 (완료율이 갱신 시점에 초기화되지 않도록).
 * 옛 플랜의 row는 그대로 남아 과거 이력으로 보존된다.
 */
@Entity
@Table(
	name = "plan_day_progress",
	uniqueConstraints = @UniqueConstraint(columnNames = { "plan_id", "week_number", "day_number" })
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class PlanDayProgress {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plan_id", nullable = false)
	private TrainingPlan trainingPlan;

	@Column(name = "week_number", nullable = false)
	private int weekNumber;

	@Column(name = "day_number", nullable = false)
	private int dayNumber;

	@Column(name = "completed", nullable = false)
	@Builder.Default
	private boolean completed = false;

	// 완료 처리(또는 평가)의 근거가 된 러닝 기록. 기록이 삭제되면 함께 정리된다.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "record_id")
	private TrainingRecord triggeringRecord;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	public void markCompleted(TrainingRecord record, LocalDateTime when) {
		this.completed = true;
		this.triggeringRecord = record;
		this.completedAt = when;
	}

	public void markIncomplete(TrainingRecord record) {
		this.completed = false;
		this.triggeringRecord = record;
		this.completedAt = null;
	}

	public void clear() {
		this.completed = false;
		this.triggeringRecord = null;
		this.completedAt = null;
	}
}