package com.example.runmateaibackend.domain.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String email;

	@Column(nullable = false, length = 255)
	private String password;

	@Column(nullable = false, length = 50)
	private String name;

	// 관리자 페이지에서 유저를 강제로 잠글 때 사용. 잠긴 계정은 로그인이 차단된다.
	@Column(nullable = false, columnDefinition = "boolean default false")
	@Builder.Default
	private boolean locked = false;

	// 신규 컬럼 - 기존 유저 데이터가 있어도 DEFAULT로 안전하게 채워지도록 columnDefinition 명시
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'USER'")
	@Builder.Default
	private Role role = Role.USER;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	public void changePassword(String newPassword) {
		this.password = newPassword;
	}

	public void lock() {
		this.locked = true;
	}

	public void unlock() {
		this.locked = false;
	}
}