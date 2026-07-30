package com.example.runmateaibackend.domain.admin.dto;

import java.time.LocalDateTime;

import com.example.runmateaibackend.domain.user.entity.User;

import lombok.Getter;

@Getter
public class AdminUserResponse {

	private final Long id;
	private final String email;
	private final String name;
	private final String role;
	private final boolean locked;
	private final LocalDateTime createdAt;

	public AdminUserResponse(User user) {
		this.id = user.getId();
		this.email = user.getEmail();
		this.name = user.getName();
		this.role = user.getRole().name();
		this.locked = user.isLocked();
		this.createdAt = user.getCreatedAt();
	}
}