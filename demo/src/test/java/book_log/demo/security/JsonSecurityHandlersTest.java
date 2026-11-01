package book_log.demo.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// 401/403 핸들러가 ErrorResponse(status, message)와 같은 JSON을 쓰는지 확인한다.
// 역할(ROLE_ADMIN 등)로 막는 경로가 아직 없어 403은 체인으로 재현할 수 없어 핸들러를 직접 호출한다
class JsonSecurityHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("AuthenticationEntryPoint는 401과 {status, message} JSON을 쓴다")
    void entryPoint() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAuthenticationEntryPoint(objectMapper)
                .commence(new MockHttpServletRequest(), response, new BadCredentialsException("내부 사유"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = objectMapper.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("message").asText()).isEqualTo(JsonAuthenticationEntryPoint.MESSAGE);
        assertThat(body.size()).isEqualTo(2);
        // 예외의 내부 사유가 응답에 새지 않는다
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("내부 사유");
    }

    @Test
    @DisplayName("AccessDeniedHandler는 403과 {status, message} JSON을 쓴다")
    void accessDenied() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAccessDeniedHandler(objectMapper)
                .handle(new MockHttpServletRequest(), response, new AccessDeniedException("내부 사유"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = objectMapper.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("message").asText()).isEqualTo(JsonAccessDeniedHandler.MESSAGE);
        assertThat(body.size()).isEqualTo(2);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("내부 사유");
    }
}
