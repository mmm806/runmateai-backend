package com.example.runmateaibackend.domain.feedback.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiFeedbackResult {

	private String feedback;
	private boolean planUpdateNeeded;
	private Object updatedPlanData; // JSON 객체 그대로 받기 위해 Object로 선언
}