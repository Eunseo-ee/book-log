# 빌드 컨텍스트는 demo/ (docker build -t book-log ./demo)

# ---- 빌드 스테이지: bootJar 생성 ----
FROM eclipse-temurin:21.0.13_9-jdk-noble AS build
WORKDIR /workspace

# 의존성 캐시: 소스보다 먼저 래퍼/빌드 파일만 복사해 의존성을 받아 둔다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp build/libs/*.jar app.jar

# ---- 런타임 스테이지: JRE + jar만 ----
FROM eclipse-temurin:21.0.13_9-jre-noble
ENV TZ=Asia/Seoul
WORKDIR /app

# 비 root 사용자 (베이스 이미지에 있는 ubuntu 계정 사용)
COPY --from=build --chown=ubuntu:ubuntu /workspace/app.jar app.jar
USER ubuntu

EXPOSE 8080

# 설정은 전부 환경변수. 포트는 PORT(없으면 8080). exec로 java가 PID 1이 되어 SIGTERM을 직접 받는다
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT:-8080} -jar app.jar"]
