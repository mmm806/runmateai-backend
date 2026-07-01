package com.example.runmateaibackend.domain.plan.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TrainingPlan.planData (AI가 생성한 JSON)에서 특정 (week, day)에 해당하는
 * 목표 거리(distance)를 찾아내는 역할만 담당한다.
 *
 * 기대하는 JSON 형태 (PlanPromptBuilder 참고):
 * {
 *   "weeks": [
 *     { "week": 1, "days": [ { "day": 1, "type": "easy run", "distance": 5, "pace": "7'00\"" }, ... ] }
 *   ]
 * }
 */
@Component
public class PlanDayLookup {

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 해당 (week, day)가 "휴식일(rest)"이 아니면서 목표 거리(distance)를 가지고 있는 경우,
	 * 그 거리를 반환한다. 휴식일이거나 해당 주차/일자를 찾지 못하면 비어있는 Optional을 반환한다.
	 */
	public Optional<BigDecimal> findPlannedDistance(String planDataJson, int week, int day) {
		JsonNode dayNode = findDayNode(planDataJson, week, day);
		if (dayNode == null) {
			return Optional.empty();
		}

		JsonNode typeNode = dayNode.get("type");
		if (typeNode != null && "rest".equalsIgnoreCase(typeNode.asText())) {
			return Optional.empty();
		}

		JsonNode distanceNode = dayNode.get("distance");
		if (distanceNode == null || distanceNode.isNull()) {
			return Optional.empty();
		}

		return Optional.of(distanceNode.decimalValue());
	}

	private JsonNode findDayNode(String planDataJson, int week, int day) {
		JsonNode root;
		try {
			root = objectMapper.readTree(planDataJson);
		} catch (Exception e) {
			return null;
		}

		JsonNode weeks = root.get("weeks");
		if (weeks == null || !weeks.isArray()) {
			return null;
		}

		for (JsonNode weekNode : weeks) {
			JsonNode weekNumNode = weekNode.get("week");
			if (weekNumNode == null || weekNumNode.asInt() != week) {
				continue;
			}

			JsonNode days = weekNode.get("days");
			if (days == null || !days.isArray()) {
				return null;
			}

			for (JsonNode dayNode : days) {
				JsonNode dayNumNode = dayNode.get("day");
				if (dayNumNode != null && dayNumNode.asInt() == day) {
					return dayNode;
				}
			}
			return null;
		}

		return null;
	}
}