package com.example.runmateaibackend.global.exception;

// 로그인 실패, 토큰 무효/만료 등 "본인 인증"에 실패했을 때 던진다. -> 401 Unauthorized
public class UnauthorizedException extends RuntimeException {
	public UnauthorizedException(String message) {
		super(message);
	}
}
