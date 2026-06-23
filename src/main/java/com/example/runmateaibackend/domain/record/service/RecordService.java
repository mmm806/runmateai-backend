package com.example.runmateaibackend.domain.record.service;

import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.plan.repository.PlanRepository;
import com.example.runmateaibackend.domain.record.dto.RecordRequest;
import com.example.runmateaibackend.domain.record.dto.RecordResponse;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import com.example.runmateaibackend.domain.record.repository.RecordRepository;
import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecordService {

	private final UserRepository userRepository;
	private final PlanRepository planRepository;
	private final RecordRepository recordRepository;

	// 러닝 기록 등록
	@Transactional
	public RecordResponse createRecord(String email, RecordRequest request) {

		User user = findUserByEmail(email);

		// 같은 날짜에 이미 기록이 있는지 확인
		if (recordRepository.findByUserAndRunDate(user, request.getRunDate()).isPresent()) {
			throw new IllegalArgumentException("해당 날짜에 이미 기록이 존재합니다.");
		}

		// 현재 활성 플랜 연결 (없으면 null)
		TrainingPlan activePlan = planRepository.findByUserAndIsActive(user, true)
			.orElse(null);

		TrainingRecord record = TrainingRecord.builder()
			.user(user)
			.trainingPlan(activePlan)
			.runDate(request.getRunDate())
			.distanceKm(request.getDistanceKm())
			.durationMin(request.getDurationMin())
			.avgPace(request.getAvgPace())
			.avgHeartRate(request.getAvgHeartRate())
			.calories(request.getCalories())
			.feeling(request.getFeeling())
			.note(request.getNote())
			.build();

		recordRepository.save(record);

		return new RecordResponse(record);
	}

	// 전체 기록 조회 (최신순)
	public List<RecordResponse> getRecords(String email) {

		User user = findUserByEmail(email);

		return recordRepository.findByUserOrderByRunDateDesc(user)
			.stream()
			.map(RecordResponse::new)
			.toList();
	}

	// 특정 날짜 기록 조회
	public RecordResponse getRecordByDate(String email, LocalDate date) {

		User user = findUserByEmail(email);

		TrainingRecord record = recordRepository.findByUserAndRunDate(user, date)
			.orElseThrow(() -> new IllegalArgumentException("해당 날짜에 기록이 없습니다."));

		return new RecordResponse(record);
	}

	// 기록 수정
	@Transactional
	public RecordResponse updateRecord(String email, Long recordId, RecordRequest request) {

		User user = findUserByEmail(email);

		TrainingRecord record = recordRepository.findById(recordId)
			.orElseThrow(() -> new IllegalArgumentException("기록을 찾을 수 없습니다."));

		// 본인 기록인지 확인 (다른 유저 기록을 수정하지 못하게)
		if (!record.getUser().getId().equals(user.getId())) {
			throw new IllegalArgumentException("본인의 기록만 수정할 수 있습니다.");
		}

		// 날짜를 변경하는 경우, 변경하려는 날짜에 이미 다른 기록이 있는지 확인
		if (!record.getRunDate().equals(request.getRunDate())) {
			recordRepository.findByUserAndRunDate(user, request.getRunDate())
				.ifPresent(existing -> {
					throw new IllegalArgumentException("해당 날짜에 이미 다른 기록이 존재합니다.");
				});
		}

		record.update(
			request.getRunDate(),
			request.getDistanceKm(),
			request.getDurationMin(),
			request.getAvgPace(),
			request.getAvgHeartRate(),
			request.getCalories(),
			request.getFeeling(),
			request.getNote()
		);

		return new RecordResponse(record);
	}

	// 기록 삭제
	@Transactional
	public void deleteRecord(String email, Long recordId) {

		User user = findUserByEmail(email);

		TrainingRecord record = recordRepository.findById(recordId)
			.orElseThrow(() -> new IllegalArgumentException("기록을 찾을 수 없습니다."));

		if (!record.getUser().getId().equals(user.getId())) {
			throw new IllegalArgumentException("본인의 기록만 삭제할 수 있습니다.");
		}

		recordRepository.delete(record);
	}

	private User findUserByEmail(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
	}
}