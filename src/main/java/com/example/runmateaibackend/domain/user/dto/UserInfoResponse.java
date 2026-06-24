package com.example.runmateaibackend.domain.user.dto;

import com.example.runmateaibackend.domain.user.entity.User;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserInfoResponse {

	private String email;
	private String name;
	private LocalDateTime createdAt;

	public UserInfoResponse(User user) {
		this.email = user.getEmail();
		this.name = user.getName();
		this.createdAt = user.getCreatedAt();
	}
}