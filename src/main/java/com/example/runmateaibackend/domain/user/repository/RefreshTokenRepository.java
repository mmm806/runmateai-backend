package com.example.runmateaibackend.domain.user.repository;

import com.example.runmateaibackend.domain.user.entity.RefreshToken;
import com.example.runmateaibackend.domain.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByUser(User user);

	Optional<RefreshToken> findByToken(String token);

	void deleteByUser(User user);
}
