package com.example.runmateaibackend.global.client;

import com.example.runmateaibackend.global.config.ClaudeApiConfig;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ClaudeApiClient {

	private final ClaudeApiConfig claudeApiConfig;
	private final ObjectMapper objectMapper = new ObjectMapper();

	// Claude API에 프롬프트를 보내고 텍스트 응답을 받아오는 메서드
	public String sendMessage(String prompt) {

		RestClient restClient = RestClient.create();

		// Claude API 요청 형식에 맞는 body 구성
		Map<String, Object> requestBody = Map.of(
			"model", claudeApiConfig.getModel(),
			"max_tokens", 4000,
			"messages", List.of(
				Map.of("role", "user", "content", prompt)
			)
		);

		// API 호출
		String response = restClient.post()
			.uri(claudeApiConfig.getUrl())
			.header("x-api-key", claudeApiConfig.getKey())
			.header("anthropic-version", "2023-06-01")
			.header("Content-Type", "application/json")
			.body(requestBody)
			.retrieve()
			.body(String.class);

		// 응답에서 실제 텍스트 부분만 추출
		return extractTextFromResponse(response);
	}

	// Claude API 응답 JSON에서 content[0].text 부분만 꺼내는 메서드
	private String extractTextFromResponse(String response) {
		try {
			JsonNode root = objectMapper.readTree(response);
			String text = root.get("content").get(0).get("text").asText();
			return cleanJsonText(text);
		} catch (Exception e) {
			throw new IllegalStateException("Claude API 응답 파싱 실패: " + e.getMessage());
		}
	}

	// 마크다운 코드블록 표시(```json, ```)를 제거하는 메서드
	private String cleanJsonText(String text) {
		return text
			.replaceAll("```json", "")
			.replaceAll("```", "")
			.trim();
	}
}