package book_log.demo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import book_log.demo.config.SecurityConfig;
import book_log.demo.dto.response.LoginResponseDto;
import book_log.demo.dto.response.MemberResponseDto;
import book_log.demo.security.JwtTokenProvider;
import book_log.demo.exception.DuplicateUsernameException;
import book_log.demo.exception.InvalidCredentialsException;
import book_log.demo.service.AuthService;

// 웹 계층만 띄운다 (DB 연결 없음)
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String PASSWORD = "plain-pass-1234";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    // SecurityConfig가 JWT 필터를 만들 때 필요하다 (이 테스트에서는 토큰을 쓰지 않는다)
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static String registerJson(String username, String password, String email) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"email\":\"" + email + "\"}";
    }

    private static String loginJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    @DisplayName("가입 성공은 201이고 id/username만 응답한다 (password 필드 없음)")
    void registerResponseHasNoPassword() throws Exception {
        when(authService.register(any())).thenReturn(new MemberResponseDto(1L, "alice"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("alice", PASSWORD, "alice@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(not(containsString(PASSWORD))));
    }

    @Test
    @DisplayName("아이디가 4자 미만 / 20자 초과면 400")
    void usernameLengthViolation() throws Exception {
        for (String username : new String[] {"abc", "a".repeat(21)}) {
            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registerJson(username, PASSWORD, "alice@example.com")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.username").exists());
        }
    }

    @Test
    @DisplayName("비밀번호가 8자 미만 / 64자 초과면 400이고, 응답에 입력한 비밀번호가 없다")
    void passwordLengthViolation() throws Exception {
        String tooLong = "p".repeat(65);
        for (String password : new String[] {"short12", tooLong}) {
            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(registerJson("alice", password, "alice@example.com")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.password").exists())
                    .andExpect(content().string(not(containsString(password))));
        }
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400")
    void emailFormatViolation() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("alice", PASSWORD, "not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());
    }

    @Test
    @DisplayName("아이디 중복은 409")
    void duplicateUsernameIs409() throws Exception {
        when(authService.register(any())).thenThrow(new DuplicateUsernameException());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("alice", PASSWORD, "alice@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다."));
    }

    @Test
    @DisplayName("로그인 성공은 200이고 password 필드가 없다")
    void loginResponseHasNoPassword() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponseDto(1L, "alice", "access-x", "refresh-x"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("alice", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("로그인 실패는 어떤 원인이든 같은 상태 코드(401)와 같은 메시지")
    void loginFailureResponseIsUniform() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        for (String username : new String[] {"nobody", "alice"}) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(username, PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(InvalidCredentialsException.MESSAGE))
                    .andExpect(content().string(not(containsString(PASSWORD))));
        }
    }
}
