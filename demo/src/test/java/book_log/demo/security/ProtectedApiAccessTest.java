package book_log.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.servlet.WebMvcEndpointManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.servlet.ServletManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import book_log.demo.config.SecurityConfig;
import book_log.demo.controller.AuthController;
import book_log.demo.controller.CalendarController;
import book_log.demo.controller.ContentController;
import book_log.demo.controller.HighlightController;
import book_log.demo.controller.SearchController;
import book_log.demo.controller.StatisticsController;
import book_log.demo.service.AuthService;
import book_log.demo.service.CalendarService;
import book_log.demo.service.ContentService;
import book_log.demo.service.HighlightService;
import book_log.demo.service.SearchService;
import book_log.demo.service.StatisticsService;

// SecurityConfig의 접근 규칙을 실제 컨트롤러 + 실제 필터 체인 + 실제 JwtTokenProvider(가짜 시크릿)로 확인한다 (DB 없음)
// actuator는 운영 설정이 어떻든 health 외에는 인증을 요구하는지 보려고, 일부러 env/beans까지 노출한 상태로 띄운다
@WebMvcTest(controllers = { AuthController.class, CalendarController.class, ContentController.class,
        HighlightController.class, SearchController.class, StatisticsController.class })
@Import({ SecurityConfig.class, ProtectedApiAccessTest.JwtTestConfig.class })
@ImportAutoConfiguration({ ManagementContextAutoConfiguration.class, ServletManagementContextAutoConfiguration.class,
        EndpointAutoConfiguration.class, WebEndpointAutoConfiguration.class,
        WebMvcEndpointManagementContextConfiguration.class, HealthContributorAutoConfiguration.class,
        HealthEndpointAutoConfiguration.class })
@TestPropertySource(properties = "management.endpoints.web.exposure.include=health,info,env,beans")
class ProtectedApiAccessTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class JwtTestConfig {
        @org.springframework.context.annotation.Bean
        JwtTokenProvider jwtTokenProvider() {
            return TestJwt.provider();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider provider;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private CalendarService calendarService;
    @MockitoBean
    private ContentService contentService;
    @MockitoBean
    private HighlightService highlightService;
    @MockitoBean
    private SearchService searchService;
    @MockitoBean
    private StatisticsService statisticsService;

    @ParameterizedTest(name = "토큰 없이 {0}")
    @ValueSource(strings = { "/api/calendar/activities?year=2026&month=4", "/api/contents/check?externalId=1&category=BOOK",
            "/api/contents/filter", "/api/highlights", "/api/search?category=BOOK&query=a", "/api/statistics" })
    @DisplayName("토큰 없이 보호 API를 호출하면 401과 ErrorResponse(status, message) JSON이 온다")
    void withoutTokenIs401(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(JsonAuthenticationEntryPoint.MESSAGE));

        verifyNoInteractions(calendarService, contentService, highlightService, searchService, statisticsService);
    }

    @Test
    @DisplayName("쓰기 요청도 토큰 없이는 401이다 (CSRF 비활성이라 인증이 유일한 방어선)")
    void writeWithoutTokenIs401() throws Exception {
        mockMvc.perform(post("/api/highlights").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"문구\",\"contentId\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(highlightService);
    }

    @Test
    @DisplayName("유효한 access 토큰이면 보호 API가 정상 응답한다")
    void withValidTokenIs200() throws Exception {
        when(highlightService.getHighlights(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/highlights").header("Authorization", "Bearer " + provider.createAccessToken(TestJwt.member(7L, "alice"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("/api/auth/**는 인증 없이 접근할 수 있다 (401이 아니다)")
    void authPathsArePublic() throws Exception {
        // 잘못된 본문이라 400이 나오는 것이 정상. 보안 규칙에 막히지 않았다는 것만 확인한다
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "/swagger-ui/index.html", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/swagger-config" })
    @DisplayName("Swagger 경로는 인증 없이 접근할 수 있다 (이 슬라이스엔 springdoc이 없어 404가 나도, 보안에 막힌 401/403은 아니다)")
    void swaggerIsPublic(String url) throws Exception {
        int status = mockMvc.perform(get(url)).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    @DisplayName("/actuator/health는 인증 없이 접근할 수 있다 (슬라이스에는 actuator 핸들러가 안 올라와 404/500이 나도, 보안에 막힌 401/403은 아니다)")
    void actuatorHealthIsPublic() throws Exception {
        for (String url : new String[] { "/actuator/health", "/actuator/health/liveness" }) {
            int status = mockMvc.perform(get(url)).andReturn().getResponse().getStatus();
            assertThat(status).isNotIn(401, 403);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "/actuator", "/actuator/env", "/actuator/beans", "/actuator/info" })
    @DisplayName("health 외 actuator 경로는 (노출 설정이 켜져 있어도) 인증 없이는 401이다")
    void otherActuatorPathsRequireAuth(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
