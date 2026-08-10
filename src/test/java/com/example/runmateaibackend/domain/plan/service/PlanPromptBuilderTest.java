package com.example.runmateaibackend.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;

/**
 * PlanPromptBuilder 단위 테스트.
 *
 * 문자열 템플릿을 조립하는 게 전부인 클래스라 로직 자체는 단순하지만,
 * 프로필의 값이 실제로 프롬프트에 정확히 반영되는지는 그 값을 근거로 AI가
 * 플랜을 생성하기 때문에 중요하다. 오타나 필드 순서 실수(예: targetWeeklyRuns와
 * targetWeeks가 뒤바뀌는 것)를 조기에 잡아내는 게 목적이다.
 */
class PlanPromptBuilderTest {

	private final PlanPromptBuilder planPromptBuilder = new PlanPromptBuilder();

	@Test
	@DisplayName("프로필의 값들이 프롬프트 문자열에 그대로 반영된다")
	void build_includesAllProfileFieldsInPrompt() {
		UserProfile profile = UserProfile.builder()
			.user(User.builder().id(1L).email("user@runmateai.com").build())
			.targetPace("5'30\"")
			.targetWeeklyRuns(4)
			.goalType("HALF_MARATHON")
			.targetWeeks(12)
			.fitnessLevel("ADVANCED")
			.monthlyGoalKm(BigDecimal.valueOf(150))
			.build();

		String prompt = planPromptBuilder.build(profile);

		assertThat(prompt)
			.contains("5'30\"")
			.contains("4회")
			.contains("HALF_MARATHON")
			.contains("12주")
			.contains("ADVANCED");
	}

	@Test
	@DisplayName("프롬프트는 AI가 순수 JSON만 응답하도록 명시적으로 요구한다")
	void build_instructsAiToRespondWithPureJsonOnly() {
		UserProfile profile = UserProfile.builder()
			.user(User.builder().id(1L).email("user@runmateai.com").build())
			.targetPace("6'00\"")
			.targetWeeklyRuns(3)
			.goalType("FIVE_K")
			.targetWeeks(4)
			.fitnessLevel("BEGINNER")
			.build();

		String prompt = planPromptBuilder.build(profile);

		// 마크다운 코드블록이나 부가 설명 없이 순수 JSON만 응답하라는 지시가 빠지면
		// ClaudeApiClient의 JSON 파싱이 깨질 수 있으므로, 이 지시문 자체가 유지되는지 확인한다.
		assertThat(prompt).contains("순수한 JSON 텍스트만 응답");
		assertThat(prompt).contains("\"weeks\"");
	}
}