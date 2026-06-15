package com.example.runmateaibackend.domain.plan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.user.entity.User;

public interface PlanRepository extends JpaRepository<TrainingPlan, Long> {

	// 유저의 현재 활성 플랜 조회
	Optional<TrainingPlan> findByUserAndIsActive(User user, boolean isActive);

	// 유저의 전체 플랜 목록 조회
	List<TrainingPlan> findByUserOrderByCreatedAtDesc(User user);
}
