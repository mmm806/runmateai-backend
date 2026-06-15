package com.example.runmateaibackend.domain.feedback.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.runmateaibackend.domain.feedback.entity.AiFeedback;
import com.example.runmateaibackend.domain.user.entity.User;

public interface FeedbackRepository extends JpaRepository<AiFeedback, Long> {

	// 유저의 전체 피드백 최신순 조회
	List<AiFeedback> findByUserOrderByCreatedAtDesc(User user);

	// 특정 기록의 피드백 조회
	List<AiFeedback> findByTrainingRecordId(Long recordId);
}
