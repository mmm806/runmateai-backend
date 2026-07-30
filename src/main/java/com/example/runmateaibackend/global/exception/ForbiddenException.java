package com.example.runmateaibackend.global.exception;

// 인증은 됐지만, 본인 소유가 아닌 리소스에 접근하려 할 때 던진다. -> 403 Forbidden
public class ForbiddenException extends RuntimeException {
	public ForbiddenException(String message) {
		super(message);
	}
}