package com.example.runmateaibackend.domain.plan.service;

import com.example.runmateaibackend.domain.user.entity.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class PlanPromptBuilder {

	public String build(UserProfile profile) {
		return """
                당신은 전문 러닝 코치입니다. 아래 사용자 정보를 바탕으로 맞춤 훈련 플랜을 JSON으로 생성해주세요.

                [사용자 정보]
                - 목표 페이스: %s
                - 목표 주간 러닝 횟수: %d회
                - 목표: %s
                - 목표 기간: %d주
                - 체력 수준: %s

                [요구사항]
                - 반드시 순수한 JSON 텍스트만 응답하세요. 마크다운 코드블록(```)이나 다른 설명 텍스트는 절대 포함하지 마세요.
                - 반드시 아래 JSON 형식으로만 응답하세요. 다른 설명 텍스트는 포함하지 마세요.
                - weeks 배열에 목표 기간만큼의 주차 데이터를 포함하세요.
                - 각 주는 7일 구성이며, 휴식일은 type을 "rest"로 표시하세요.
                - 점진적으로 난이도를 높여가는 구조로 만들어주세요.

                [JSON 형식]
                {
                  "weeks": [
                    {
                      "week": 1,
                      "days": [
                        { "day": 1, "type": "easy run", "distance": 5, "pace": "7'00\\"" },
                        { "day": 2, "type": "rest" },
                        { "day": 3, "type": "tempo run", "distance": 6, "pace": "6'30\\"" }
                      ]
                    }
                  ]
                }
                """.formatted(
			profile.getTargetPace(),
			profile.getTargetWeeklyRuns(),
			profile.getGoalType(),
			profile.getTargetWeeks(),
			profile.getFitnessLevel()
		);
	}
}