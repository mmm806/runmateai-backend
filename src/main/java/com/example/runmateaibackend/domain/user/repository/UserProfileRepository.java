package com.example.runmateaibackend.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.runmateaibackend.domain.user.entity.User;
import com.example.runmateaibackend.domain.user.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

	// 유저로 프로필 조회
	Optional<UserProfile> findByUser(User user);
}
