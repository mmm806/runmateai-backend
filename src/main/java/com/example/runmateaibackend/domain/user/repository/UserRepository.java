package com.example.runmateaibackend.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.runmateaibackend.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	// 이메일로 유저 조회
	Optional<User> findByEmail(String email);

	// 이메일 중복 확인
	boolean existsByEmail(String email);
}
