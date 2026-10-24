package book_log.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import book_log.demo.security.TestJwt;

// 설정 검증이 앱 시작(컨텍스트 로딩) 단계에서 실패로 이어지는지 확인한다. 값은 모두 가짜
class JwtPropertiesTest {

    @EnableConfigurationProperties
    @Import(JwtProperties.class)
    static class Cfg {
    }

    private static final String SHORT_SECRET = TestJwt.base64("too-short-fake");
    private static final String VALID_SECRET = TestJwt.FAKE_SECRET;

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Cfg.class);

    @Test
    @DisplayName("정상 설정이면 컨텍스트가 뜬다")
    void valid() {
        runner.withPropertyValues("jwt.secret=" + VALID_SECRET, "jwt.access-token-expiration=1000",
                "jwt.refresh-token-expiration=2000")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    @DisplayName("시크릿이 없으면 시작이 실패하고, 메시지에 키 이름이 있다")
    void missingSecret() {
        runner.withPropertyValues("jwt.access-token-expiration=1000", "jwt.refresh-token-expiration=2000")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(rootMessage(ctx.getStartupFailure())).contains("jwt.secret");
                });
    }

    @Test
    @DisplayName("디코딩한 시크릿이 32바이트 미만이면 실패하고, 메시지에 시크릿 값이 없다")
    void shortSecret() {
        runner.withPropertyValues("jwt.secret=" + SHORT_SECRET, "jwt.access-token-expiration=1000",
                "jwt.refresh-token-expiration=2000")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(rootMessage(ctx.getStartupFailure())).contains("32바이트").doesNotContain(SHORT_SECRET);
                });
    }

    @Test
    @DisplayName("BASE64가 아닌 시크릿이면 실패하고, 메시지에 시크릿 값이 없다")
    void notBase64() {
        String notBase64 = "!!!not-base64-at-all!!!";
        runner.withPropertyValues("jwt.secret=" + notBase64, "jwt.access-token-expiration=1000",
                "jwt.refresh-token-expiration=2000")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(rootMessage(ctx.getStartupFailure())).contains("BASE64").doesNotContain(notBase64);
                });
    }

    @Test
    @DisplayName("만료 시간이 없거나 0 이하이면 실패한다")
    void invalidExpirations() {
        runner.withPropertyValues("jwt.secret=" + VALID_SECRET, "jwt.refresh-token-expiration=2000")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("jwt.secret=" + VALID_SECRET, "jwt.access-token-expiration=0",
                "jwt.refresh-token-expiration=2000")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("jwt.secret=" + VALID_SECRET, "jwt.access-token-expiration=1000")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }
}
