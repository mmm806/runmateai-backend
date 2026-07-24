package com.example.runmateaibackend.domain.plan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.user.entity.User;

public interface PlanRepository extends JpaRepository<TrainingPlan, Long> {

	// 유저의 현재 활성 플랜 조회
	// 정상 상태라면 활성 플랜은 항상 1개여야 하지만(one_active_plan_per_user 유니크 인덱스로 보장),
	// 혹시 모를 데이터 이상 상황에서도 예외 없이 동작하도록 "가장 최근 것 1개"만 안전하게 조회한다.
	// (기존 findByUserAndIsActive는 결과가 2개 이상이면 IncorrectResultSizeDataAccessException을 던져 서비스가 죽었음)
	Optional<TrainingPlan> findFirstByUserAndIsActiveOrderByCreatedAtDesc(User user, boolean isActive);

	// 유저의 전체 플랜 목록 조회
	List<TrainingPlan> findByUserOrderByCreatedAtDesc(User user);

	// 유저 조회후 플랜 삭제
	void deleteByUser(User user);


	Optional<TrainingPlan> findFirstByUserOrderByCreatedAtAsc(User user);
}