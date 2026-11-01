package book_log.demo.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;

import book_log.demo.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 인증이 필요한데 없거나 잘못된 요청(토큰 없음/위조/만료)에 401을 기존 ErrorResponse(status, message) JSON으로 응답한다
// 시큐리티 필터 단계의 예외는 @RestControllerAdvice가 잡지 못해, 같은 형식을 여기서 직접 쓴다
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String MESSAGE = "인증이 필요합니다.";

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        // 토큰이 없는지 위조인지는 응답에 드러내지 않는다 (같은 메시지)
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), MESSAGE));
    }
}
