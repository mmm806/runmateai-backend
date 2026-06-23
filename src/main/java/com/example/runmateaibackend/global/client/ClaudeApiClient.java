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

	public String sendMessage(String prompt) {

		RestClient restClient = RestClient.create();

		Map<String, Object> requestBody = Map.of(
			"model", claudeApiConfig.getModel(),
			"max_tokens", 4000,
			"messages", List.of(
				Map.of("role", "user", "content", prompt)
			)
		);

		String response = restClient.post()
			.uri(claudeApiConfig.getUrl())
			.header("x-api-key", claudeApiConfig.getKey())
			.header("anthropic-version", "2023-06-01")
			.header("Content-Type", "application/json")
			.body(requestBody)
			.retrieve()
			.body(String.class);

		return extractTextFromResponse(response);
	}

	// <T> 제네릭으로 원하는 타입으로 바로 파싱해주는 메서드 추가
	public <T> T sendMessageAndParse(String prompt, Class<T> targetClass) {
		String jsonText = sendMessage(prompt);
		try {
			return objectMapper.readValue(jsonText, targetClass);
		} catch (Exception e) {
			throw new IllegalStateException("AI 응답 JSON 파싱 실패: " + e.getMessage());
		}
	}

	private String extractTextFromResponse(String response) {
		try {
			JsonNode root = objectMapper.readTree(response);
			String text = root.get("content").get(0).get("text").asText();
			return cleanJsonText(text);
		} catch (Exception e) {
			throw new IllegalStateException("Claude API 응답 파싱 실패: " + e.getMessage());
		}
	}

	private String cleanJsonText(String text) {
		return text
			.replaceAll("```json", "")
			.replaceAll("```", "")
			.trim();
	}
}