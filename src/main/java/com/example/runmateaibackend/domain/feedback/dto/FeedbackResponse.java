package com.example.runmateaibackend.domain.feedback.dto;

import com.example.runmateaibackend.domain.feedback.entity.AiFeedback;
import lombok.Getter;

@Getter
public class FeedbackResponse {

	private Long id;
	private String feedbackContent;
	private boolean planUpdated;

	public FeedbackResponse(AiFeedback feedback) {
		this.id = feedback.getId();
		this.feedbackContent = feedback.getFeedbackContent();
		this.planUpdated = feedback.isPlanUpdated();
	}
}