package book_log.demo.config;

import book_log.demo.controller.HighlightController;
import book_log.demo.controller.StatisticsController;
import book_log.demo.security.JwtTokenProvider;
import book_log.demo.service.HighlightService;
import book_log.demo.service.StatisticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 웹 계층만 띄운다 (DB 연결 없음). SecurityConfig는 슬라이스 스캔 대상이 아니라 직접 Import
@WebMvcTest(controllers = {HighlightController.class, StatisticsController.class})
@Import(SecurityConfig.class)
class CorsConfigTest {

    private static final String ALLOWED = "http://localhost:3000";
    private static final String NOT_ALLOWED = "http://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HighlightService highlightService;

    @MockitoBean
    private StatisticsService statisticsService;

    // SecurityConfig가 JWT 필터를 만들 때 필요하다 (이 테스트에서는 토큰을 쓰지 않는다)
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static MockHttpServletRequestBuilder preflight(String url, String origin, String method) {
        return options(url)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type");
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "GET, /api/highlights",
            "POST, /api/highlights",
            "PUT, /api/highlights/1",
            "DELETE, /api/highlights/1",
    })
    @DisplayName("허용 origin의 preflight는 200이고 Allow-Origin/Allow-Methods 헤더가 온다")
    void allowedPreflight(String method, String url) throws Exception {
        mockMvc.perform(preflight(url, ALLOWED, method))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString(method)));
    }

    @Test
    @DisplayName("허용하지 않은 origin의 preflight는 403이고 Allow-Origin 헤더가 없다")
    void rejectedPreflight() throws Exception {
        mockMvc.perform(preflight("/api/highlights", NOT_ALLOWED, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("실제 요청도 허용 origin이면 통과하고 Allow-Origin 헤더가 붙는다")
    void allowedActualRequest() throws Exception {
        when(highlightService.getHighlights(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/highlights").header(HttpHeaders.ORIGIN, ALLOWED))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
    }

    @Test
    @DisplayName("실제 요청도 허용하지 않은 origin이면 403")
    void rejectedActualRequest() throws Exception {
        mockMvc.perform(get("/api/highlights").header(HttpHeaders.ORIGIN, NOT_ALLOWED))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("@CrossOrigin(\"*\")을 제거한 통계 API도 전역 규칙을 따른다")
    void statisticsFollowsGlobalRule() throws Exception {
        mockMvc.perform(preflight("/api/statistics", ALLOWED, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));

        mockMvc.perform(preflight("/api/statistics", NOT_ALLOWED, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("preflight는 시큐리티 필터에서 처리되어, 이 슬라이스에 없는 컨트롤러 경로(/api/contents)도 같은 규칙이다")
    void preflightHandledBySecurityFilter() throws Exception {
        mockMvc.perform(preflight("/api/contents", ALLOWED, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
    }

    @Nested
    @TestPropertySource(properties = "app.cors.allowed-origins=https://book-log.example.com,http://localhost:3000")
    @DisplayName("app.cors.allowed-origins로 허용 origin을 바꿀 수 있다")
    class CustomOrigins {

        // 바깥 클래스의 mockMvc는 바깥(기본 설정) 컨텍스트에서 주입된다. 이 클래스 컨텍스트의 MockMvc를 따로 받는다
        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("설정한 origin은 모두 허용한다")
        void configuredOriginsAllowed() throws Exception {
            mockMvc.perform(preflight("/api/highlights", "https://book-log.example.com", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://book-log.example.com"));

            mockMvc.perform(preflight("/api/highlights", ALLOWED, "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
        }

        @Test
        @DisplayName("설정하지 않은 origin은 여전히 거절한다")
        void otherOriginRejected() throws Exception {
            mockMvc.perform(preflight("/api/highlights", NOT_ALLOWED, "GET"))
                    .andExpect(status().isForbidden());
        }
    }
}
