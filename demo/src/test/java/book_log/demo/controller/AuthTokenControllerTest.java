package book_log.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import book_log.demo.config.SecurityConfig;
import book_log.demo.domain.Member;
import book_log.demo.exception.InvalidCredentialsException;
import book_log.demo.exception.InvalidTokenException;
import book_log.demo.repository.MemberRepository;
import book_log.demo.security.JwtTokenProvider;
import book_log.demo.security.TestJwt;
import book_log.demo.security.TokenType;
import book_log.demo.service.AuthService;

// 컨트롤러 + 실제 AuthService + 실제 JwtTokenProvider(가짜 시크릿) + 실제 BCrypt. DB만 Mockito (연결 없음)
@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, AuthService.class, AuthTokenControllerTest.JwtTestConfig.class })
class AuthTokenControllerTest {

    private static final String PASSWORD = "plain-pass-1234";

    @TestConfiguration
    static class JwtTestConfig {
        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return TestJwt.provider();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider provider;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberRepository memberRepository;

    private Member alice;

    @BeforeEach
    void setUp() {
        alice = Member.builder().username("alice").password(passwordEncoder.encode(PASSWORD))
                .email("alice@example.com").build();
        ReflectionTestUtils.setField(alice, "id", 7L);
    }

    private static String json(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            sb.append(i > 0 ? "," : "").append('"').append(keyValues[i]).append("\":\"").append(keyValues[i + 1])
                    .append('"');
        }
        return sb.append('}').toString();
    }

    private MvcResult login() throws Exception {
        when(memberRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("username", "alice", "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
    }

    @Test
    @DisplayName("로그인 응답에 id/username/accessToken/refreshToken이 있고, 비밀번호와 해시는 없다")
    void loginReturnsTokens() throws Exception {
        MvcResult result = login();
        String body = result.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);

        assertThat(node.get("id").asLong()).isEqualTo(7L);
        assertThat(node.get("username").asText()).isEqualTo("alice");
        assertThat(provider.parse(node.get("accessToken").asText(), TokenType.ACCESS)).isPresent();
        assertThat(provider.parse(node.get("refreshToken").asText(), TokenType.REFRESH)).isPresent();
        assertThat(node.has("password")).isFalse();
        assertThat(body).doesNotContain(PASSWORD).doesNotContain(alice.getPassword()).doesNotContain("$2");
    }

    @Test
    @DisplayName("로그인 실패는 이전과 같은 401과 같은 메시지이고 토큰이 없다")
    void loginFailureStillUniform() throws Exception {
        when(memberRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(memberRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        for (String[] attempt : new String[][] { { "alice", "wrong-pass-0000" }, { "nobody", PASSWORD } }) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json("username", attempt[0], "password", attempt[1])))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(InvalidCredentialsException.MESSAGE))
                    .andExpect(jsonPath("$.accessToken").doesNotExist());
        }
    }

    @Test
    @DisplayName("refresh 성공: 새 access 토큰만 내려오고 refresh는 회전하지 않는다")
    void refreshSuccess() throws Exception {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(alice));
        String refreshToken = provider.createRefreshToken(alice);

        MvcResult result = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        String newAccess = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        assertThat(provider.parse(newAccess, TokenType.ACCESS)).isPresent();
    }

    @Test
    @DisplayName("refresh 엔드포인트에 access 토큰을 보내면 401 (ErrorResponse 형식)")
    void refreshRejectsAccessToken() throws Exception {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(alice));

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", provider.createAccessToken(alice))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(InvalidTokenException.MESSAGE));
    }

    @Test
    @DisplayName("refresh에 위조/만료/쓰레기 토큰을 보내면 모두 같은 401")
    void refreshRejectsInvalidTokens() throws Exception {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(alice));
        JwtTokenProvider expiring = new JwtTokenProvider(
                TestJwt.properties(TestJwt.FAKE_SECRET, TestJwt.ACCESS_MS, -1_000L));
        JwtTokenProvider otherSecret = new JwtTokenProvider(
                TestJwt.properties(TestJwt.OTHER_FAKE_SECRET, TestJwt.ACCESS_MS, TestJwt.REFRESH_MS));
        String valid = provider.createRefreshToken(alice);

        String[] tokens = { expiring.createRefreshToken(alice), otherSecret.createRefreshToken(alice),
                valid.substring(0, valid.length() - 3) + "xyz", "garbage" };
        for (String token : tokens) {
            mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                    .content(json("refreshToken", token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(InvalidTokenException.MESSAGE))
                    .andExpect(content().string(not(containsString(token))));
        }
    }

    @Test
    @DisplayName("refresh 토큰이 유효해도 회원이 삭제됐으면 401")
    void refreshRejectsDeletedMember() throws Exception {
        when(memberRepository.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", provider.createRefreshToken(alice))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(InvalidTokenException.MESSAGE));
    }

    @Test
    @DisplayName("refreshToken이 비어 있으면 400")
    void refreshBlankIs400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    @DisplayName("비밀번호 72바이트 이내 한글(24자=72바이트)은 가입 통과")
    void koreanPasswordWithin72BytesAccepted() throws Exception {
        when(memberRepository.existsByUsername("alice")).thenReturn(false);
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(inv -> {
            Member m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 9L);
            return m;
        });
        String password = "가".repeat(24);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("username", "alice", "password", password, "email", "alice@example.com")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("비밀번호가 72바이트를 넘으면(한글 25자=75바이트, 64자 이내) 400이고 이유를 알려준다")
    void koreanPasswordOver72BytesRejected() throws Exception {
        String password = "가".repeat(25);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("username", "alice", "password", password, "email", "alice@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").value(containsString("72바이트")))
                .andExpect(content().string(not(containsString(password))));
    }

    @Test
    @DisplayName("영문 64자(64바이트)는 가입 통과: 문자 수·바이트 수 모두 한도 이내")
    void asciiPasswordAtLimitAccepted() throws Exception {
        when(memberRepository.existsByUsername("alice")).thenReturn(false);
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("username", "alice", "password", "p".repeat(64), "email", "alice@example.com")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("72바이트를 크게 넘는 비밀번호(한글 64자=192바이트)로 로그인해도 500이 아니라 같은 401")
    void veryLongPasswordLoginIsUniform401() throws Exception {
        when(memberRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(memberRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        String longPassword = "가".repeat(64);

        for (String username : new String[] { "alice", "nobody" }) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json("username", username, "password", longPassword)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(InvalidCredentialsException.MESSAGE));
        }
    }
}
