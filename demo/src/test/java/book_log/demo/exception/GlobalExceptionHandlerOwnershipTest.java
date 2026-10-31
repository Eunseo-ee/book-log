package book_log.demo.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import book_log.demo.controller.HighlightController;
import book_log.demo.security.JwtTokenProvider;
import book_log.demo.service.HighlightService;

// 웹 계층만 띄운다 (DB 연결 없음)
@WebMvcTest(HighlightController.class)
@Import(SecurityConfig.class)
class GlobalExceptionHandlerOwnershipTest {

    private static final String BODY = "{\"text\":\"문구\",\"contentId\":1}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HighlightService highlightService;

    // SecurityConfig가 JWT 필터를 만들 때 필요하다
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("남의 하이라이트 수정은 403과 ErrorResponse(status, message)로 응답한다")
    void forbiddenIs403() throws Exception {
        when(highlightService.updateHighlight(anyLong(), anyLong(), any()))
                .thenThrow(new ForbiddenResourceException());

        mockMvc.perform(put("/api/highlights/10").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(ForbiddenResourceException.MESSAGE));
    }

    @Test
    @DisplayName("없는 하이라이트 삭제는 404와 ErrorResponse로 응답한다")
    void notFoundIs404() throws Exception {
        doThrow(new ResourceNotFoundException("해당 하이라이트가 없습니다. id=404"))
                .when(highlightService).deleteHighlight(anyLong(), anyLong());

        mockMvc.perform(delete("/api/highlights/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("해당 하이라이트가 없습니다. id=404"));
    }

    @Test
    @DisplayName("기존 IllegalArgumentException은 여전히 400이다")
    void illegalArgumentStill400() throws Exception {
        when(highlightService.updateHighlight(anyLong(), anyLong(), any()))
                .thenThrow(new IllegalArgumentException("잘못된 값"));

        mockMvc.perform(put("/api/highlights/10").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
