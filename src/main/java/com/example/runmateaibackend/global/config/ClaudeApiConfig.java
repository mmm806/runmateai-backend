package com.example.runmateaibackend.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "claude.api")
@Getter
@Setter
public class ClaudeApiConfig {

	private String key;
	private String url;
	private String model;
}