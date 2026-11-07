package book_log.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import book_log.demo.security.JwtTokenProvider;
import book_log.demo.security.TestJwt;

// 실제 앱 컨텍스트를 prod 프로파일로 띄워 운영 노출 설정을 확인한다 (H2 test 설정 + 가짜 값)
// Redis는 일부러 닫힌 포트(1)로 향하게 해, Redis가 죽었을 때의 health 그룹 동작을 같이 본다
@SpringBootTest(properties = {
        "app.cors.allowed-origins=https://app.example.com",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.ssl.enabled=false",
        "spring.data.redis.timeout=500ms",
        "spring.data.redis.connect-timeout=500ms"
})
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "prod" })
class ProdExposureTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private Environment env;
    @Autowired
    private WebEndpointsSupplier webEndpoints;
    @Autowired
    private JwtTokenProvider provider;

    private String bearer() {
        return "Bearer " + provider.createAccessToken(TestJwt.member(7L, "alice"));
    }

    // ---- Swagger ----

    @Test
    @DisplayName("prod: springdoc 빈이 아예 만들어지지 않는다")
    void springdocBeansAbsent() {
        assertThat(context.getBeanNamesForType(org.springdoc.core.service.OpenAPIService.class)).isEmpty();
        assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(env.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs", "/v3/api-docs/swagger-config" })
    @DisplayName("prod: Swagger/api-docs는 인증 없이 401 (permitAll 아님)")
    void swaggerAnonymousIs401(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs" })
    @DisplayName("prod: 로그인한 사용자에게도 api-docs 본문이 나가지 않는다 (200 아님)")
    void swaggerAuthenticatedIsNot200(String url) throws Exception {
        var response = mockMvc.perform(get(url).header("Authorization", bearer())).andReturn().getResponse();
        assertThat(response.getStatus()).isNotEqualTo(200);
        assertThat(response.getContentAsString()).doesNotContain("openapi").doesNotContain("paths");
    }

    // ---- actuator 노출 ----

    @Test
    @DisplayName("prod: 웹에 노출된 actuator 엔드포인트는 health 하나뿐이다")
    void onlyHealthIsExposed() {
        List<String> ids = webEndpoints.getEndpoints().stream().map(e -> e.getEndpointId().toString()).toList();
        assertThat(ids).containsExactly("health");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "/actuator", "/actuator/env", "/actuator/beans", "/actuator/configprops",
            "/actuator/heapdump", "/actuator/threaddump", "/actuator/mappings", "/actuator/loggers",
            "/actuator/info", "/actuator/metrics", "/actuator/shutdown" })
    @DisplayName("prod: 민감 actuator 경로는 익명이면 401")
    void sensitiveActuatorAnonymousIs401(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump",
            "/actuator/threaddump", "/actuator/mappings", "/actuator/loggers", "/actuator/info" })
    @DisplayName("prod: 토큰이 있어도 민감 actuator 경로는 200이 아니다 (노출되지 않음)")
    void sensitiveActuatorAuthenticatedIsNot200(String url) throws Exception {
        var response = mockMvc.perform(get(url).header("Authorization", bearer())).andReturn().getResponse();
        assertThat(response.getStatus()).isNotEqualTo(200);
        assertThat(response.getContentAsString()).doesNotContain("propertySources").doesNotContain("contexts");
    }

    // ---- 헬스체크 ----

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "/actuator/health/liveness", "/actuator/health/readiness" })
    @DisplayName("prod: liveness/readiness는 인증 없이 200이고 본문은 status뿐이다")
    void probeGroupsArePublicAndMinimal(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }

    @Test
    @DisplayName("prod: Redis가 죽어도 liveness와 readiness는 UP이다 (컨테이너가 재시작/차단되지 않음)")
    void probesStayUpWhenRedisIsDown() throws Exception {
        // 전체 health는 redis 포함이라 DOWN(503)이어야 한다. 이게 아니면 이 테스트가 Redis 장애를 만들지 못한 것
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());

        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "/actuator/health/redis", "/actuator/health/db", "/actuator/health/diskSpace" })
    @DisplayName("prod: 구성요소별 health 경로는 상세를 보여주지 않는다 (200 아님)")
    void componentPathsAreHidden(String url) throws Exception {
        var response = mockMvc.perform(get(url)).andReturn().getResponse();
        assertThat(response.getStatus()).isNotEqualTo(200);
        assertThat(response.getContentAsString()).doesNotContain("PostgreSQL").doesNotContain("H2")
                .doesNotContain("database").doesNotContain("free");
    }

    // ---- 에러 응답 ----

    @Test
    @DisplayName("prod: server.error는 메시지/스택트레이스/예외를 싣지 않도록 고정되어 있다")
    void serverErrorProperties() {
        assertThat(env.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(env.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(env.getProperty("server.error.include-binding-errors")).isEqualTo("never");
        assertThat(env.getProperty("server.error.include-exception")).isEqualTo("false");
    }

    @Test
    @DisplayName("prod: 없는 경로의 에러 본문에 스택트레이스/예외 클래스가 없다")
    void errorBodyHasNoStackTrace() throws Exception {
        String body = mockMvc.perform(get("/no-such-path").header("Authorization", bearer()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("trace").doesNotContain("Exception").doesNotContain("at book_log")
                .doesNotContain("at org.springframework");
    }
}
