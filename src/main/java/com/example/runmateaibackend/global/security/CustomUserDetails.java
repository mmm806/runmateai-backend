package com.example.runmateaibackend.global.security;

import com.example.runmateaibackend.domain.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

	private final User user;

	public CustomUserDetails(User user) {
		this.user = user;
	}

	// 권한 목록 - User.role에 따라 ROLE_USER 또는 ROLE_ADMIN 반환
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
	}

	// 비밀번호 반환
	@Override
	public String getPassword() {
		return user.getPassword();
	}

	// 로그인 ID로 이메일 사용
	@Override
	public String getUsername() {
		return user.getEmail();
	}

	// 계정 만료 여부 (true = 만료 안 됨)
	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	// 계정 잠김 여부 (true = 잠기지 않음) - 관리자가 잠근 계정은 false 반환
	@Override
	public boolean isAccountNonLocked() {
		return !user.isLocked();
	}

	// 비밀번호 만료 여부 (true = 만료 안 됨)
	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	// 계정 활성화 여부 (true = 활성화)
	@Override
	public boolean isEnabled() {
		return true;
	}
}