package com.example.runmateaibackend.domain.record.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class BestRecordInfo {
	private BigDecimal distanceKm;
	private String pace;
	private int durationMin;
	private LocalDate runDate;
}