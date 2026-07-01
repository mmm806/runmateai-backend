package com.example.runmateaibackend.domain.plan.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.runmateaibackend.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "training_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class TrainingPlan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "plan_data", nullable = false, columnDefinition = "json")
	@JdbcTypeCode(SqlTypes.JSON)
	private String planData;

	@Column(name = "goal_type", nullable = false, length = 20)
	private String goalType;

	// 플랜 1주차 1일째에 해당하는 실제 날짜. 플랜 생성일의 다음 날로 고정된다.
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "is_active", nullable = false)
	@Builder.Default
	private boolean isActive = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	public void deactivate() { // 새로운 플랜을 생성할때 기존 플랜 비활성화
		this.isActive = false;
	}

	public void activate() {
		this.isActive = true;
	}

	/**
	 * 실제 날짜(date)가 이 플랜에서 몇 주차 며칠째에 해당하는지 계산한다.
	 * week, day는 모두 1부터 시작한다 (week 1, day 1 = startDate).
	 * startDate 이전 날짜이면 비어있는 Optional을 반환한다.
	 */
	public Optional<int[]> resolveWeekAndDay(LocalDate date) {
		long offset = ChronoUnit.DAYS.between(this.startDate, date);
		if (offset < 0) {
			return Optional.empty();
		}
		int week = (int) (offset / 7) + 1;
		int day = (int) (offset % 7) + 1;
		return Optional.of(new int[] { week, day });
	}

}