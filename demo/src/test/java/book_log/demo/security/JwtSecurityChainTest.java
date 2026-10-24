package book_log.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import book_log.demo.config.SecurityConfig;
import book_log.demo.controller.HighlightController;
import book_log.demo.domain.Member;
import book_log.demo.service.HighlightService;
import io.jsonwebtoken.Jwts;

// SecurityConfig의 실제 필터 체인(STATELESS + JWT 필터 + permitAll + CORS)을 웹 계층에서 확인한다
@WebMvcTest(controllers = { HighlightController.class, JwtSecurityChainTest.ProbeController.class })
@Import({ SecurityConfig.class, JwtSecurityChainTest.JwtTestConfig.class, JwtSecurityChainTest.ProbeController.class })
class JwtSecurityChainTest {

    @TestConfiguration
    static class JwtTestConfig {
        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return TestJwt.provider();
        }
    }

    // @AuthenticationPrincipal AuthenticatedMember로 principal을 꺼낼 수 있는지 확인하는 테스트 전용 컨트롤러
    @RestController
    static class ProbeController {
        @GetMapping("/api/probe/me")
        String me(@AuthenticationPrincipal AuthenticatedMember member) {
            return member == null ? "anonymous" : member.id() + ":" + member.username();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider provider;
    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private HighlightService highlightService;

    private final Member member = TestJwt.member(7L, "alice");

    @Test
    @DisplayName("유효한 access 토큰이면 컨트롤러가 @AuthenticationPrincipal AuthenticatedMember로 id를 받는다")
    void principalInjected() throws Exception {
        mockMvc.perform(get("/api/probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.createAccessToken(member)))
                .andExpect(status().isOk())
                .andExpect(content().string("7:alice"));
    }

    @Test
    @DisplayName("refresh 토큰으로는 인증되지 않는다 (익명으로 통과)")
    void refreshTokenIsAnonymous() throws Exception {
        mockMvc.perform(get("/api/probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.createRefreshToken(member)))
                .andExpect(status().isOk())
                .andExpect(content().string("anonymous"));
    }

    @Test
    @DisplayName("위조/만료/깨진 토큰이 담긴 요청도 permitAll 경로에서는 401/500 없이 정상 처리된다")
    void badTokensDoNotBreakPermitAllPaths() throws Exception {
        when(highlightService.getHighlights(any(), any())).thenReturn(List.of());
        JwtTokenProvider expiring = new JwtTokenProvider(
                TestJwt.properties(TestJwt.FAKE_SECRET, -1_000L, TestJwt.REFRESH_MS));
        JwtTokenProvider otherSecret = new JwtTokenProvider(
                TestJwt.properties(TestJwt.OTHER_FAKE_SECRET, TestJwt.ACCESS_MS, TestJwt.REFRESH_MS));
        String unsigned = Jwts.builder().subject("7").claim("username", "alice").claim("role", "ROLE_USER")
                .claim("type", "access").expiration(new Date(System.currentTimeMillis() + 60_000)).compact();

        String[] headers = { "Bearer garbage", "Bearer " + expiring.createAccessToken(member),
                "Bearer " + otherSecret.createAccessToken(member), "Bearer " + unsigned, "Bearer ", "Basic abc" };
        for (String header : headers) {
            mockMvc.perform(get("/api/highlights").header(HttpHeaders.AUTHORIZATION, header))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/probe/me").header(HttpHeaders.AUTHORIZATION, header))
                    .andExpect(status().isOk())
                    .andExpect(content().string("anonymous"));
        }
    }

    @Test
    @DisplayName("CORS preflight가 Authorization 헤더를 허용한다 (allowedHeaders=*는 요청한 헤더를 그대로 허용)")
    void corsAllowsAuthorizationHeader() throws Exception {
        mockMvc.perform(options("/api/highlights")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsStringIgnoringCase("authorization")));
    }

    @Test
    @DisplayName("JwtAuthenticationFilter는 빈이 아니다 (서블릿 필터로 자동 등록되어 두 번 실행되는 일이 없다)")
    void filterIsNotABean() {
        assertThat(context.getBeansOfType(JwtAuthenticationFilter.class)).isEmpty();
    }
}
