package com.example.runmateaibackend.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 통합 테스트 공통 베이스 클래스.
 *
 * H2 같은 인메모리 DB 대신 실제 PostgreSQL을 Docker 컨테이너로 띄워서 테스트한다.
 * 이 프로젝트는 부분 유니크 인덱스(one_active_plan_per_user), advisory lock처럼
 * PostgreSQL 전용 기능에 의존하고 있어, 다른 DB로는 정확한 재현이 불가능하기 때문이다.
 *
 * @ServiceConnection이 컨테이너의 접속 정보를 Spring에 자동으로 연결해주므로
 * 별도의 datasource 설정을 직접 오버라이드할 필요가 없다.
 * 컨테이너가 뜬 뒤에는 Flyway가 애플리케이션 시작 시점에 자동으로 스키마를 구성한다.
 */
@SpringBootTest
@Testcontainers
public abstract class IntegrationTestSupport {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
}