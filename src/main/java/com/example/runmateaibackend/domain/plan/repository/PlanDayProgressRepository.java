package com.example.runmateaibackend.domain.plan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.runmateaibackend.domain.plan.entity.PlanDayProgress;
import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;

public interface PlanDayProgressRepository extends JpaRepository<PlanDayProgress, Long> {

	Optional<PlanDayProgress> findByTrainingPlanAndWeekNumberAndDayNumber(
		TrainingPlan trainingPlan, int weekNumber, int dayNumber);

	List<PlanDayProgress> findByTrainingPlan(TrainingPlan trainingPlan);

	// 해당 기록으로 인해 완료/평가 처리된 진행 상태 (기록 수정·삭제 시 되돌리기 위함)
	List<PlanDayProgress> findByTriggeringRecord(TrainingRecord triggeringRecord);
}