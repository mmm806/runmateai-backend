# ================================
# 1단계: 빌드
# ================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Gradle Wrapper와 설정 파일만 먼저 복사 (의존성 캐시 레이어 활용)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 의존성 먼저 다운로드 (소스 변경 시 이 레이어는 캐시 재사용)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon 2>/dev/null || true

# 소스 코드 복사 후 빌드
COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon -x test

# ================================
# 2단계: 실행 (JRE만 포함한 경량 이미지)
# ================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 빌드 결과물만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 비루트 유저로 실행 (보안)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]