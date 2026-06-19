package com.example.runmateaibackend.domain.feedback.service;

import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class FeedbackPromptBuilder {

	public String build(TrainingRecord latestRecord, List<TrainingRecord> recentRecords) {

		String recentRecordsText = recentRecords.stream()
			.map(this::formatRecord)
			.collect(Collectors.joining("\n"));

		return """
                당신은 전문 러닝 코치입니다. 아래 사용자의 최근 러닝 기록을 분석하고 피드백을 제공해주세요.

                [오늘 기록]
                %s

                [최근 기록 (최신순)]
                %s

                [요구사항]
                - 오늘 기록에 대한 구체적인 피드백을 2~4문장으로 작성하세요.
                - 페이스, 거리, 컨디션 변화 추세를 고려하세요.
                - 격려와 함께 다음 훈련을 위한 조언을 포함하세요.
                - 반드시 순수한 텍스트로만 응답하세요. 마크다운이나 JSON 형식은 사용하지 마세요.
                """.formatted(
			formatRecord(latestRecord),
			recentRecordsText
		);
	}

	private String formatRecord(TrainingRecord record) {
		return String.format(
			"날짜: %s, 거리: %skm, 시간: %d분, 페이스: %s, 컨디션: %s",
			record.getRunDate(),
			record.getDistanceKm(),
			record.getDurationMin(),
			record.getAvgPace(),
			record.getFeeling() != null ? record.getFeeling() : "기록 없음"
		);
	}
}