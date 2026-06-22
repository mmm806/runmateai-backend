package com.example.runmateaibackend.domain.record.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.user.entity.User;

public interface RecordRepository extends JpaRepository<TrainingRecord, Long> {

	// 유저의 전체 기록 최신순 조회
	List<TrainingRecord> findByUserOrderByRunDateDesc(User user);

	// 특정 날짜 기록 조회 (날짜 중복 방지)
	Optional<TrainingRecord> findByUserAndRunDate(User user, LocalDate runDate);

	// 유저의 최근 N개 기록 조회
	List<TrainingRecord> findTop5ByUserOrderByRunDateDesc(User user);

	// 유저 조회후 기록 삭제
	void deleteByUser(User user);
}
