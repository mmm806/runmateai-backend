package com.example.runmateaibackend.domain.feedback.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.runmateaibackend.domain.feedback.entity.AiFeedback;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.user.entity.User;

public interface FeedbackRepository extends JpaRepository<AiFeedback, Long> {

	// 유저의 전체 피드백 최신순 조회
	List<AiFeedback> findByUserOrderByCreatedAtDesc(User user);

	// 플랜이 업데이트된 피드백 개수만 카운트 (전체 피드백을 로드하지 않고 COUNT 쿼리로 처리)
	long countByUserAndPlanUpdatedTrue(User user);

	// 특정 기록의 피드백 조회
	List<AiFeedback> findByTrainingRecordId(Long recordId);

	// 유저 조회후 피드백 삭제
	void deleteByUser(User user);

	void deleteByTrainingRecord(TrainingRecord trainingRecord);
}