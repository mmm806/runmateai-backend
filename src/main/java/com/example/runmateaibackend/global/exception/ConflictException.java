package com.example.runmateaibackend.global.exception;

// 이미 존재하는 리소스를 중복 생성하려 하거나, 현재 서버 상태와 요청이 충돌할 때 던진다. -> 409 Conflict
public class ConflictException extends RuntimeException {
	public ConflictException(String message) {
		super(message);
	}
}
