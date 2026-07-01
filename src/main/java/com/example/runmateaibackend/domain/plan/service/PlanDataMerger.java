package com.example.runmateaibackend.domain.plan.service;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * AI 피드백으로 플랜이 갱신될 때, "이미 지나간 (week, day)는 옛 플랜 내용을 그대로 유지하고,
 * 오늘 이후의 (week, day)만 AI가 새로 짠 내용으로 교체"하도록 두 플랜을 강제로 병합한다.
 *
 * AI에게 프롬프트로 "지난 일정은 건드리지 말라"고 부탁만 해서는, AI가 그 지시를 어기고
 * 과거 일정까지 다시 써버릴 위험이 있다. 이 클래스는 AI의 응답 내용과 무관하게,
 * 코드 레벨에서 과거 일정이 절대 바뀌지 않도록 보장한다.
 */
@Component
public class PlanDataMerger {

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * @param oldPlanDataJson 갱신 전 플랜의 planData (JSON 문자열)
	 * @param newPlanDataJson AI가 새로 생성한 planData (JSON 문자열)
	 * @param planStartDate   플랜의 1주차 1일째에 해당하는 실제 날짜
	 * @param today           기준이 되는 오늘 날짜 (today 이전 = 과거, today 포함 이후 = 미래)
	 * @return 과거는 oldPlanDataJson, 오늘 이후는 newPlanDataJson을 기준으로 병합된 JSON 문자열
	 */
	public String merge(String oldPlanDataJson, String newPlanDataJson, LocalDate planStartDate, LocalDate today) {

		JsonNode oldRoot = objectMapper.readTree(oldPlanDataJson);
		JsonNode newRoot = objectMapper.readTree(newPlanDataJson);

		JsonNode oldWeeks = oldRoot.get("weeks");
		JsonNode newWeeks = newRoot.get("weeks");

		if (newWeeks == null || !newWeeks.isArray()) {
			// 새 플랜 형식이 비정상이면 안전하게 옛 플랜을 그대로 반환한다.
			return oldPlanDataJson;
		}

		// AI는 "앞으로 남은 주차만" 응답에 담을 수 있다 (지난 주차를 통째로 생략).
		// 그래서 순회 기준을 새 플랜 하나가 아니라, 옛 플랜 + 새 플랜의 전체 주차 번호
		// 합집합으로 잡아야 지난 주차가 누락되지 않는다.
		java.util.TreeSet<Integer> allWeekNumbers = new java.util.TreeSet<>();
		collectWeekNumbers(oldWeeks, allWeekNumbers);
		collectWeekNumbers(newWeeks, allWeekNumbers);

		ObjectNode mergedRoot = objectMapper.createObjectNode();
		ArrayNode mergedWeeks = objectMapper.createArrayNode();

		for (int week : allWeekNumbers) {

			ObjectNode mergedWeekNode = objectMapper.createObjectNode();
			mergedWeekNode.put("week", week);

			ArrayNode mergedDays = objectMapper.createArrayNode();

			// 이 주차에 대해 옛 플랜/새 플랜 각각에 존재하는 일자 번호의 합집합을 구한다.
			java.util.TreeSet<Integer> allDayNumbers = new java.util.TreeSet<>();
			JsonNode oldWeekNode = findWeekNode(oldWeeks, week);
			JsonNode newWeekNode = findWeekNode(newWeeks, week);
			collectDayNumbers(oldWeekNode, allDayNumbers);
			collectDayNumbers(newWeekNode, allDayNumbers);

			for (int day : allDayNumbers) {
				LocalDate dayDate = resolveDate(planStartDate, week, day);
				JsonNode oldDayNode = findDayNodeInWeek(oldWeekNode, day);
				JsonNode newDayNode = findDayNodeInWeek(newWeekNode, day);

				if (dayDate.isBefore(today)) {
					// 과거 날짜: 옛 플랜의 내용을 우선 사용한다. 옛 플랜에 없으면(이론상 드묾)
					// 새 플랜 내용으로라도 채운다.
					mergedDays.add(oldDayNode != null ? oldDayNode : newDayNode);
				} else {
					// 오늘 이후: AI가 새로 짠 내용을 우선 사용한다. 새 플랜에 그 (week, day)가
					// 없다면(AI가 누락) 옛 플랜 내용으로 대체해 빈 구멍이 생기지 않게 한다.
					mergedDays.add(newDayNode != null ? newDayNode : oldDayNode);
				}
			}

			mergedWeekNode.set("days", mergedDays);
			mergedWeeks.add(mergedWeekNode);
		}

		mergedRoot.set("weeks", mergedWeeks);
		return mergedRoot.toString();
	}

	private LocalDate resolveDate(LocalDate startDate, int week, int day) {
		long offset = (long) (week - 1) * 7 + (day - 1);
		return startDate.plusDays(offset);
	}

	private void collectWeekNumbers(JsonNode weeksArray, java.util.Set<Integer> into) {
		if (weeksArray == null || !weeksArray.isArray()) {
			return;
		}
		for (JsonNode weekNode : weeksArray) {
			into.add(weekNode.path("week").asInt());
		}
	}

	private JsonNode findWeekNode(JsonNode weeksArray, int week) {
		if (weeksArray == null || !weeksArray.isArray()) {
			return null;
		}
		for (JsonNode weekNode : weeksArray) {
			if (weekNode.path("week").asInt() == week) {
				return weekNode;
			}
		}
		return null;
	}

	private void collectDayNumbers(JsonNode weekNode, java.util.Set<Integer> into) {
		if (weekNode == null) {
			return;
		}
		JsonNode days = weekNode.get("days");
		if (days == null || !days.isArray()) {
			return;
		}
		for (JsonNode dayNode : days) {
			into.add(dayNode.path("day").asInt());
		}
	}

	private JsonNode findDayNodeInWeek(JsonNode weekNode, int day) {
		if (weekNode == null) {
			return null;
		}
		JsonNode days = weekNode.get("days");
		if (days == null || !days.isArray()) {
			return null;
		}
		for (JsonNode dayNode : days) {
			if (dayNode.path("day").asInt() == day) {
				return dayNode;
			}
		}
		return null;
	}
}