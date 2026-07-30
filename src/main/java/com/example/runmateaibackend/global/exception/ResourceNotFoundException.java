package com.example.runmateaibackend.global.exception;

// 요청한 리소스(유저, 기록, 플랜, 프로필 등)가 존재하지 않을 때 던진다. -> 404 Not Found
public class ResourceNotFoundException extends RuntimeException {
	public ResourceNotFoundException(String message) {
		super(message);
	}
}
