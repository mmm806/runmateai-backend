package com.example.runmateaibackend.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * PlanDataMerger 단위 테스트.
 *
 * 이 클래스의 핵심 불변식은 "과거 날짜는 무조건 옛 플랜을 유지하고, 오늘 이후는 새 플랜을
 * 우선하되, 어느 한쪽에 데이터가 없으면 반대쪽 내용으로 안전하게 대체한다"이다.
 * AI 응답 내용이 어떻든 이 불변식이 코드 레벨에서 깨지지 않는지를 검증하는 게 목적이라,
 * 실제 merge() 결과 JSON을 파싱해서 각 (week, day)가 어느 쪽 플랜 내용으로 채워졌는지
 * 직접 확인한다.
 */
class PlanDataMergerTest {

	private final PlanDataMerger planDataMerger = new PlanDataMerger();
	private final ObjectMapper objectMapper = new ObjectMapper();

	private JsonNode findDay(String mergedJson, int week, int day) {
		JsonNode weeks = objectMapper.readTree(mergedJson).get("weeks");
		for (JsonNode weekNode : weeks) {
			if (weekNode.get("week").asInt() != week) {
				continue;
			}
			for (JsonNode dayNode : weekNode.get("days")) {
				if (dayNode.get("day").asInt() == day) {
					return dayNode;
				}
			}
		}
		throw new AssertionError("병합 결과에서 %d주차 %d일째를 찾지 못했다".formatted(week, day));
	}

	@Test
	@DisplayName("새 플랜이 JSON 형식이 아니거나 weeks 배열이 없으면, 안전하게 옛 플랜을 그대로 반환한다")
	void merge_invalidNewPlanFormat_returnsOldPlanUnchanged() {
		String oldPlan = """
			{"weeks":[{"week":1,"days":[{"day":1,"type":"easy run","distance":5}]}]}
			""".strip();
		String malformedNewPlan = "{}"; // weeks 키가 아예 없음

		String result = planDataMerger.merge(oldPlan, malformedNewPlan, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 8));

		assertThat(result).isEqualTo(oldPlan);
	}

	@Test
	@DisplayName("과거 주차는 새 플랜에서 생략되어 있어도 옛 플랜 내용이 그대로 유지되고, " +
		"오늘 이후 날짜는 새 플랜에 있는 내용으로 교체된다")
	void merge_pastDatesKeepOldContent_futureDatesUseNewContent() {
		// AI에게 매주 목~수(1주=7일)로 시작하는 플랜을 준다고 가정: 1주차=1/1~1/7, 2주차=1/8~1/14
		LocalDate planStartDate = LocalDate.of(2026, 1, 1);
		LocalDate today = LocalDate.of(2026, 1, 8); // 2주차 1일째 = 오늘

		String oldPlan = """
			{
			  "weeks": [
			    { "week": 1, "days": [
			        { "day": 1, "type": "easy run", "distance": 5 },
			        { "day": 2, "type": "rest" }
			    ]},
			    { "week": 2, "days": [
			        { "day": 1, "type": "tempo run", "distance": 6 },
			        { "day": 2, "type": "rest" }
			    ]}
			  ]
			}
			""";

		// AI 피드백으로 재생성된 플랜: 이미 지난 1주차는 통째로 생략하고 2주차만 응답에 담았다.
		String newPlan = """
			{
			  "weeks": [
			    { "week": 2, "days": [
			        { "day": 1, "type": "tempo run", "distance": 8 },
			        { "day": 2, "type": "easy run", "distance": 4 }
			    ]}
			  ]
			}
			""";

		String merged = planDataMerger.merge(oldPlan, newPlan, planStartDate, today);

		// 1주차(과거, 1/1~1/7)는 새 플랜에 아예 없었지만 옛 플랜 내용 그대로 살아있어야 한다.
		assertThat(findDay(merged, 1, 1).get("type").asText()).isEqualTo("easy run");
		assertThat(findDay(merged, 1, 1).get("distance").asInt()).isEqualTo(5);
		assertThat(findDay(merged, 1, 2).get("type").asText()).isEqualTo("rest");

		// 2주차(오늘 이후)는 새 플랜 내용으로 교체되어야 한다.
		assertThat(findDay(merged, 2, 1).get("type").asText()).isEqualTo("tempo run");
		assertThat(findDay(merged, 2, 1).get("distance").asInt()).isEqualTo(8);
		assertThat(findDay(merged, 2, 2).get("type").asText()).isEqualTo("easy run");
		assertThat(findDay(merged, 2, 2).get("distance").asInt()).isEqualTo(4);
	}

	@Test
	@DisplayName("미래 날짜인데 새 플랜이 그 주차는 포함하면서도 특정 일자를 빠뜨렸다면, " +
		"빈 구멍이 생기지 않도록 옛 플랜 내용으로 안전하게 대체한다")
	void merge_futureDateMissingInNewPlan_fallsBackToOldContent() {
		LocalDate planStartDate = LocalDate.of(2026, 1, 1);
		LocalDate today = LocalDate.of(2026, 1, 8);

		String oldPlan = """
			{
			  "weeks": [
			    { "week": 2, "days": [
			        { "day": 1, "type": "tempo run", "distance": 6 },
			        { "day": 2, "type": "rest" }
			    ]}
			  ]
			}
			""";

		// AI가 2주차 응답에 1일째만 담고 2일째를 실수로 빠뜨린 상황.
		String newPlan = """
			{
			  "weeks": [
			    { "week": 2, "days": [
			        { "day": 1, "type": "tempo run", "distance": 9 }
			    ]}
			  ]
			}
			""";

		String merged = planDataMerger.merge(oldPlan, newPlan, planStartDate, today);

		assertThat(findDay(merged, 2, 1).get("type").asText()).isEqualTo("tempo run");
		assertThat(findDay(merged, 2, 1).get("distance").asInt()).isEqualTo(9); // 새 플랜 값 채택
		assertThat(findDay(merged, 2, 2).get("type").asText()).isEqualTo("rest"); // 옛 플랜으로 대체
	}

	@Test
	@DisplayName("과거 날짜인데 옛 플랜에 그 일자가 없다면(이론상 드묾), 새 플랜 내용으로 대체한다")
	void merge_pastDateMissingInOldPlan_fallsBackToNewContent() {
		LocalDate planStartDate = LocalDate.of(2026, 1, 1);
		LocalDate today = LocalDate.of(2026, 1, 10); // 1주차 전체가 과거

		String oldPlan = """
			{
			  "weeks": [
			    { "week": 1, "days": [
			        { "day": 1, "type": "easy run", "distance": 5 }
			    ]}
			  ]
			}
			""";

		String newPlan = """
			{
			  "weeks": [
			    { "week": 1, "days": [
			        { "day": 1, "type": "easy run", "distance": 5 },
			        { "day": 2, "type": "tempo run", "distance": 7 }
			    ]}
			  ]
			}
			""";

		String merged = planDataMerger.merge(oldPlan, newPlan, planStartDate, today);

		assertThat(findDay(merged, 1, 1).get("distance").asInt()).isEqualTo(5); // 옛 플랜에 있으므로 옛 것 우선
		assertThat(findDay(merged, 1, 2).get("type").asText()).isEqualTo("tempo run"); // 옛 플랜에 없으므로 새 것으로 대체
	}
}