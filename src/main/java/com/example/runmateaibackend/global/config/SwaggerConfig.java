package com.example.runmateaibackend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI openAPI() {
		String jwtSchemeName = "Bearer Authentication";

		SecurityRequirement securityRequirement = new SecurityRequirement()
			.addList(jwtSchemeName);

		SecurityScheme securityScheme = new SecurityScheme()
			.name(jwtSchemeName)
			.type(SecurityScheme.Type.HTTP)
			.scheme("bearer")
			.bearerFormat("JWT")
			.description("로그인 후 발급받은 Access Token을 입력하세요. (Bearer 접두사 없이 토큰만 입력)");

		return new OpenAPI()
			.info(new Info()
				.title("RunMate AI API")
				.description("AI 기반 러닝 코칭 서비스 RunMate AI의 REST API 문서입니다.")
				.version("1.0.0"))
			.addSecurityItem(securityRequirement)
			.components(new Components()
				.addSecuritySchemes(jwtSchemeName, securityScheme));
	}
}