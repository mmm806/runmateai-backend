package com.example.runmateaibackend.domain.feedback.dto;

import com.example.runmateaibackend.domain.feedback.entity.AiFeedback;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class FeedbackResponse {

	private Long id;
	private String feedbackContent;
	private boolean planUpdated;
	private LocalDateTime createdAt;

	public FeedbackResponse(AiFeedback feedback) {
		this.id = feedback.getId();
		this.feedbackContent = feedback.getFeedbackContent();
		this.planUpdated = feedback.isPlanUpdated();
		this.createdAt = feedback.getCreatedAt();
	}
}