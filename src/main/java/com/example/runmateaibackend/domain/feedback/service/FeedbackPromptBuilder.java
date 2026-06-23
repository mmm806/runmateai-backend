package com.example.runmateaibackend.domain.feedback.service;

import com.example.runmateaibackend.domain.plan.entity.TrainingPlan;
import com.example.runmateaibackend.domain.record.entity.TrainingRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class FeedbackPromptBuilder {

	public String build(TrainingRecord latestRecord, List<TrainingRecord> recentRecords, TrainingPlan currentPlan) {

		String recentRecordsText = recentRecords.stream()
			.map(this::formatRecord)
			.collect(Collectors.joining("\n"));

		return """
                당신은 전문 러닝 코치입니다. 아래 사용자의 최근 러닝 기록을 분석하고,
                피드백을 제공하며, 필요하다면 남은 훈련 플랜을 조정해주세요.

                [오늘 기록]
                %s

                [최근 기록 (최신순)]
                %s

                [현재 훈련 플랜]
                %s

                [요구사항]
                - 오늘 기록을 분석해서 컨디션, 페이스, 거리 추세를 판단하세요.
                - 너무 무리하고 있다면(feeling이 나쁘거나, 페이스가 계획보다 너무 빠른 경우) 
                  남은 훈련 강도를 낮추는 방향으로 플랜을 조정하세요.
                - 컨디션이 매우 좋다면(feeling이 좋고 여유로운 경우) 
                  남은 훈련 강도를 살짝 높이는 방향으로 조정할 수 있습니다.
                - 특별한 문제가 없다면 플랜은 조정하지 않아도 됩니다.
                - 반드시 순수한 JSON 텍스트만 응답하세요. 마크다운 코드블록이나 다른 설명은 포함하지 마세요.

                [응답 JSON 형식]
                {
                  "feedback": "사용자에게 보여줄 피드백 문장 (2~4문장)",
                  "planUpdateNeeded": true 또는 false,
                  "updatedPlanData": planUpdateNeeded가 true일 때만 포함,
                                     현재 플랜과 동일한 구조의 전체 JSON 객체 (남은 주차 전체를 다시 작성)
                }
                """.formatted(
			formatRecord(latestRecord),
			recentRecordsText,
			currentPlan.getPlanData()
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