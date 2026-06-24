package com.example.runmateaibackend.domain.record.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
public class RecordRequest {

	@NotNull(message = "달린 날짜는 필수입니다.")
	private LocalDate runDate;

	@NotNull(message = "거리는 필수입니다.")
	@Positive(message = "거리는 0보다 커야 합니다.")
	private BigDecimal distanceKm;

	@NotNull(message = "시간은 필수입니다.")
	@Positive(message = "시간은 0보다 커야 합니다.")
	private Integer durationMin;

	@NotBlank(message = "평균 페이스는 필수입니다.")
	private String avgPace;

	private Integer avgHeartRate;

	private Integer calories;

	private String feeling;

	private String note;

	private Integer elevationGain; // 선택 입력, 미터 단위
}