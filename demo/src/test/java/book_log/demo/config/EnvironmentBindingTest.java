package book_log.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.util.TestPropertyValues.Type;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.context.annotation.Import;

// 운영에서는 설정을 환경변수로 주입한다. 어떤 환경변수 이름이 어떤 프로퍼티에 바인딩되는지 실제로 확인한다. 값은 모두 가짜
class EnvironmentBindingTest {

    @EnableConfigurationProperties
    @Import(ApiConfig.class)
    static class Cfg {
    }

    // ApiConfig의 restTemplate 빈이 RestTemplateBuilder를 요구하므로 직접 등록한다
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Cfg.class)
            .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new);

    private ApplicationContextRunner withEnv(String... vars) {
        // 실제 OS 환경변수 소스(systemEnvironment)와 같은 종류인 SystemEnvironmentPropertySource로 넣는다
        return runner.withInitializer(ctx -> TestPropertyValues.of(vars).applyTo(ctx.getEnvironment(), Type.SYSTEM_ENVIRONMENT));
    }

    @Test
    @DisplayName("API_KAKAOKEY, API_TMDBTOKEN(-를 뺀 형태)은 kakaoKey/tmdbToken에 바인딩된다")
    void noUnderscoreInsideName() {
        withEnv("API_KAKAOKEY=k", "API_TMDBTOKEN=t").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(ApiConfig.class).getKakaoKey()).isEqualTo("k");
            assertThat(ctx.getBean(ApiConfig.class).getTmdbToken()).isEqualTo("t");
        });
    }

    @Test
    @DisplayName("API_KAKAO_KEY, API_TMDB_TOKEN(kebab-case 이름의 -를 _로 바꾼 형태)도 바인딩된다")
    void underscoreInsideName() {
        withEnv("API_KAKAO_KEY=k", "API_TMDB_TOKEN=t").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(ApiConfig.class).getKakaoKey()).isEqualTo("k");
            assertThat(ctx.getBean(ApiConfig.class).getTmdbToken()).isEqualTo("t");
        });
    }

    @Test
    @DisplayName("키가 하나라도 없거나 공백이면 시작이 실패하고, 메시지에 프로퍼티 이름이 있다")
    void missingOrBlankKey() {
        withEnv("API_TMDBTOKEN=t").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(rootMessages(ctx)).contains("kakaoKey");
        });
        withEnv("API_KAKAOKEY=k").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(rootMessages(ctx)).contains("tmdbToken");
        });
        withEnv("API_KAKAOKEY=   ", "API_TMDBTOKEN=t").run(ctx -> assertThat(ctx).hasFailed());
        runner.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("JWT_* 환경변수가 JwtProperties에 바인딩되는 이름을 확인한다")
    void jwtNames() {
        new ApplicationContextRunner().withUserConfiguration(JwtCfg.class)
                .withInitializer(ctx -> TestPropertyValues.of("JWT_SECRET=" + book_log.demo.security.TestJwt.FAKE_SECRET,
                        "JWT_ACCESSTOKENEXPIRATION=1000", "JWT_REFRESHTOKENEXPIRATION=2000")
                        .applyTo(ctx.getEnvironment(), Type.SYSTEM_ENVIRONMENT))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(JwtProperties.class).getAccessTokenExpiration()).isEqualTo(1000L);
                });
    }

    @EnableConfigurationProperties
    @Import(JwtProperties.class)
    static class JwtCfg {
    }

    @Test
    @DisplayName("@Value(app.cors.allowed-origins)는 APP_CORS_ALLOWED_ORIGINS로 읽힌다")
    void corsValue() {
        StandardEnvironment env = envOf(Map.of("APP_CORS_ALLOWED_ORIGINS", "https://a.example"));
        assertThat(env.getProperty("app.cors.allowed-origins")).isEqualTo("https://a.example");
    }

    @Test
    @DisplayName("spring.* 운영 설정 이름: ddl-auto, show-sql, redis, datasource")
    void springNames() {
        StandardEnvironment env = envOf(Map.of(
                "SPRING_JPA_HIBERNATE_DDL_AUTO", "validate",
                "SPRING_JPA_SHOW_SQL", "false",
                "SPRING_DATASOURCE_URL", "jdbc:h2:mem:x",
                "SPRING_DATA_REDIS_SSL_ENABLED", "true"));
        Binder binder = new Binder(ConfigurationPropertySources.get(env));
        assertThat(binder.bind("spring.jpa.hibernate.ddl-auto", String.class).get()).isEqualTo("validate");
        assertThat(binder.bind("spring.jpa.show-sql", Boolean.class).get()).isFalse();
        assertThat(binder.bind("spring.datasource.url", String.class).get()).isEqualTo("jdbc:h2:mem:x");
        assertThat(binder.bind("spring.data.redis.ssl.enabled", Boolean.class).get()).isTrue();
    }

    @Test
    @DisplayName("format_sql처럼 밑줄이 들어간 맵 키는 어떤 환경변수 이름으로도 만들 수 없다")
    void formatSqlEnvName() {
        assertThat(bindJpaProperties(Map.of("SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT", "x")))
                .containsEntry("hibernate.dialect", "x");
        for (String name : new String[] { "SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL",
                "SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT__SQL", "SPRING_JPA_PROPERTIES_HIBERNATE_FORMATSQL" }) {
            assertThat(bindJpaProperties(Map.of(name, "false"))).doesNotContainKey("hibernate.format_sql");
        }
    }

    private static Map<String, String> bindJpaProperties(Map<String, String> env) {
        Binder binder = new Binder(ConfigurationPropertySources.from(envOf(env).getPropertySources().get(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)));
        return binder.bind("spring.jpa.properties", Bindable.mapOf(String.class, String.class)).orElse(Map.of());
    }

    private static StandardEnvironment envOf(Map<String, ?> vars) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.copyOf(vars)));
        return env;
    }

    private static String rootMessages(AssertableApplicationContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = ctx.getStartupFailure(); t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
