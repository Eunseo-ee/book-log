package book_log.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

// 허용 origin 값의 파싱·검증과, 환경변수 APP_CORS_ALLOWED_ORIGINS가 실제 CORS 설정까지 이어지는지 확인한다 (Spring 컨텍스트 없이)
class CorsOriginsTest {

    @Test
    @DisplayName("쉼표로 나눠 공백과 끝 슬래시를 지우고 빈 값은 버린다")
    void trimsSlashesAndSkipsBlanks() {
        assertThat(CorsOrigins.parse("  https://a.example/ , http://localhost:3000//,, ,https://b.example  "))
                .containsExactly("https://a.example", "http://localhost:3000", "https://b.example");
    }

    @Test
    @DisplayName("중복은 하나로 합치고, 비었거나 null이면 빈 목록")
    void dedupesAndEmpty() {
        assertThat(CorsOrigins.parse("https://a.example,https://a.example/")).containsExactly("https://a.example");
        assertThat(CorsOrigins.parse("")).isEmpty();
        assertThat(CorsOrigins.parse(" , ,/ ")).isEmpty();
        assertThat(CorsOrigins.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("와일드카드(*)는 단독이든 일부든 거부한다")
    void rejectsWildcard() {
        assertThatThrownBy(() -> CorsOrigins.parse("*")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CorsOrigins.parse("https://a.example,*")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CorsOrigins.parse("https://*.vercel.app")).isInstanceOf(IllegalStateException.class);
    }

    // 실제 OS 환경변수와 같은 종류의 소스(systemEnvironment)로 넣어 relaxed binding을 그대로 탄다
    private static StandardEnvironment envWith(Map<String, Object> vars, String... profiles) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, vars));
        env.setActiveProfiles(profiles);
        return env;
    }

    private static CorsConfiguration configFor(StandardEnvironment env) {
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(env);
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/highlights"));
    }

    @Test
    @DisplayName("APP_CORS_ALLOWED_ORIGINS 환경변수가 지저분한 값 그대로 허용 목록이 된다")
    void envVarReachesCorsConfiguration() {
        CorsConfiguration config = configFor(envWith(
                Map.of("APP_CORS_ALLOWED_ORIGINS", " https://book-log.example.com/ , http://localhost:3000 ")));

        assertThat(config.getAllowedOrigins()).containsExactly("https://book-log.example.com", "http://localhost:3000");
        assertThat(config.checkOrigin("https://book-log.example.com")).isEqualTo("https://book-log.example.com");
        assertThat(config.checkOrigin("https://evil.example.com")).isNull();
        assertThat(config.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("값이 없으면 로컬에서는 localhost:3000만 허용한다")
    void devFallsBackToLocalhost() {
        assertThat(configFor(envWith(Map.of())).getAllowedOrigins()).isEqualTo(List.of("http://localhost:3000"));
        assertThat(configFor(envWith(Map.of("APP_CORS_ALLOWED_ORIGINS", " , "))).getAllowedOrigins())
                .isEqualTo(List.of("http://localhost:3000"));
    }

    @Test
    @DisplayName("prod 프로파일에서 값이 없거나 비어 있으면 기동 실패(예외)")
    void prodRequiresOrigins() {
        assertThatThrownBy(() -> configFor(envWith(Map.of(), "prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_CORS_ALLOWED_ORIGINS");
        assertThatThrownBy(() -> configFor(envWith(Map.of("APP_CORS_ALLOWED_ORIGINS", " , "), "prod")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("prod 프로파일이어도 값이 있으면 localhost를 몰래 추가하지 않는다")
    void prodUsesOnlyConfiguredOrigins() {
        CorsConfiguration config = configFor(envWith(Map.of("APP_CORS_ALLOWED_ORIGINS", "https://book-log.example.com"), "prod"));
        assertThat(config.getAllowedOrigins()).containsExactly("https://book-log.example.com");
    }

    @Test
    @DisplayName("와일드카드를 환경변수로 넣어도 기동 실패(예외)")
    void wildcardFromEnvFails() {
        assertThatThrownBy(() -> configFor(envWith(Map.of("APP_CORS_ALLOWED_ORIGINS", "*"))))
                .isInstanceOf(IllegalStateException.class);
    }
}
