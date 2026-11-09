package book_log.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import book_log.demo.DemoApplication;

/**
 * 25-f: 이미지가 prod 프로파일로 뜨고, prod에서 CORS origin이 없으면 앱이 실제로 기동 실패하는지 확인한다.
 * 이미지 빌드/실행은 하지 않는다(Docker 없이 Dockerfile 텍스트와 앱 기동만 본다).
 */
class ProdProfileDefaultTest {

    @Test
    @DisplayName("Dockerfile의 런타임 스테이지가 SPRING_PROFILES_ACTIVE=prod 기본값을 가진다")
    void dockerfileDefaultsToProdProfile() throws IOException {
        // Gradle 테스트의 작업 디렉터리는 demo/ 이다
        List<String> lines = Files.readAllLines(Path.of("Dockerfile"));

        int runtimeStage = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().toUpperCase().startsWith("FROM ")) {
                runtimeStage = i; // 마지막 FROM = 런타임 스테이지
            }
        }
        assertThat(runtimeStage).isGreaterThanOrEqualTo(0);

        List<String> runtimeLines = lines.subList(runtimeStage, lines.size()).stream().map(String::trim).toList();
        assertThat(runtimeLines).contains("ENV SPRING_PROFILES_ACTIVE=prod");
    }

    @Test
    @DisplayName("prod 프로파일에서 APP_CORS_ALLOWED_ORIGINS가 비어 있으면 앱 기동이 실패한다 (메서드가 아니라 실제 기동)")
    void prodStartupFailsWithoutCorsOrigins() {
        // 커맨드라인 인자는 최우선이라 로컬 application.yml이 있어도 origin을 비운 상태가 보장된다
        assertThatThrownBy(() -> new SpringApplicationBuilder(DemoApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--spring.profiles.active=test,prod", "--server.port=0", "--app.cors.allowed-origins="))
                .hasStackTraceContaining("APP_CORS_ALLOWED_ORIGINS");
    }
}
