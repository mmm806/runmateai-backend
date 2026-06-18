package com.example.runmateaibackend.domain.record.controller;

import com.example.runmateaibackend.domain.record.dto.RecordRequest;
import com.example.runmateaibackend.domain.record.dto.RecordResponse;
import com.example.runmateaibackend.domain.record.service.RecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordController {

	private final RecordService recordService;

	// 러닝 기록 등록
	@PostMapping
	public ResponseEntity<RecordResponse> createRecord(
		Authentication authentication,
		@Valid @RequestBody RecordRequest request
	) {
		String email = authentication.getName();
		RecordResponse response = recordService.createRecord(email, request);
		return ResponseEntity.ok(response);
	}

	// 전체 기록 조회
	@GetMapping
	public ResponseEntity<List<RecordResponse>> getRecords(Authentication authentication) {
		String email = authentication.getName();
		List<RecordResponse> responses = recordService.getRecords(email);
		return ResponseEntity.ok(responses);
	}

	// 특정 날짜 기록 조회
	@GetMapping("/{date}")
	public ResponseEntity<RecordResponse> getRecordByDate(
		Authentication authentication,
		@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
	) {
		String email = authentication.getName();
		RecordResponse response = recordService.getRecordByDate(email, date);
		return ResponseEntity.ok(response);
	}
}