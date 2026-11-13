package book_log.demo.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import book_log.demo.config.SecurityConfig;
import book_log.demo.config.TimeConfig;
import book_log.demo.controller.CalendarController;
import book_log.demo.controller.ContentController;
import book_log.demo.controller.HighlightController;
import book_log.demo.controller.SearchController;
import book_log.demo.controller.StatisticsController;
import book_log.demo.domain.Content;
import book_log.demo.domain.Highlight;
import book_log.demo.domain.Member;
import book_log.demo.repository.ContentRepository;
import book_log.demo.repository.HighlightRepository;
import book_log.demo.security.JwtTokenProvider;
import book_log.demo.security.TestAuth;
import book_log.demo.service.CalendarService;
import book_log.demo.service.ContentService;
import book_log.demo.service.HighlightService;
import book_log.demo.service.SearchService;
import book_log.demo.service.StatisticsService;

// 예외 응답 정리(task 35): 입력 오류는 400, 없는 리소스는 404, 생성은 201 + Location.
// 컨트롤러·GlobalExceptionHandler·HighlightService는 실제 코드이고, 서비스/저장소 의존성만 mock이다.
// 수정 전에는 1a/1b/2/6/7이 500, 4가 400, 5가 200이었다.
@WebMvcTest(controllers = { CalendarController.class, SearchController.class, StatisticsController.class,
        HighlightController.class, ContentController.class })
@Import({ SecurityConfig.class, TimeConfig.class, HighlightService.class })
class ExceptionResponseTest {

    private static final long ME = 7L;
    private static final String HIGHLIGHT_BODY = "{\"text\":\"문구\",\"contentId\":%d}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalendarService calendarService;
    @MockitoBean
    private ContentService contentService;
    @MockitoBean
    private SearchService searchService;
    @MockitoBean
    private StatisticsService statisticsService;
    @MockitoBean
    private HighlightRepository highlightRepository;
    @MockitoBean
    private ContentRepository contentRepository;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("캘린더 month가 범위 밖이면 400이고 위반 필드가 메시지에 담긴다")
    void calendarMonthOutOfRangeIs400() throws Exception {
        mockMvc.perform(get("/api/calendar/activities").param("year", "2026").param("month", "13")
                .with(TestAuth.member(ME)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("month")));
    }

    @Test
    @DisplayName("캘린더 year가 범위 밖이면 400이다")
    void calendarYearOutOfRangeIs400() throws Exception {
        mockMvc.perform(get("/api/calendar/activities").param("year", "1999").param("month", "4")
                .with(TestAuth.member(ME)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("year")));
    }

    @Test
    @DisplayName("검색 category가 Enum에 없으면 400이고 입력값은 에코하지 않는다")
    void searchInvalidCategoryIs400() throws Exception {
        mockMvc.perform(get("/api/search").param("category", "잘못된값").param("query", "abc")
                .with(TestAuth.member(ME)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("category")))
                .andExpect(content().string(not(containsString("잘못된값"))));
    }

    @Test
    @DisplayName("숫자 파라미터에 숫자가 아닌 값이 오면 400이다 (타입 변환 핸들러의 부수 효과 고정)")
    void nonNumericYearIs400() throws Exception {
        mockMvc.perform(get("/api/calendar/activities").param("year", "abc").param("month", "4")
                .with(TestAuth.member(ME)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("abc"))));
    }

    @Test
    @DisplayName("검색 query가 없으면 400이다")
    void searchMissingQueryIs400() throws Exception {
        mockMvc.perform(get("/api/search").param("category", "BOOK").with(TestAuth.member(ME)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("query")));
    }

    @Test
    @DisplayName("깨진 JSON body는 400이고 body 내용은 응답에 에코하지 않는다")
    void brokenJsonIs400() throws Exception {
        mockMvc.perform(post("/api/contents").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"비밀제목\"").with(TestAuth.member(ME)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(content().string(not(containsString("비밀제목"))));
    }

    @Test
    @DisplayName("통계 month가 범위 밖이면 400이다 (변경 없음)")
    void statisticsMonthOutOfRangeIs400() throws Exception {
        mockMvc.perform(get("/api/statistics").param("month", "13").with(TestAuth.member(ME)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 contentId로 하이라이트를 저장하면 404와 ErrorResponse이다")
    void saveHighlightWithMissingContentIs404() throws Exception {
        when(contentRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/highlights").contentType(MediaType.APPLICATION_JSON)
                .content(HIGHLIGHT_BODY.formatted(999L)).with(TestAuth.member(ME)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("존재하지 않는 콘텐츠입니다. id=999"));
    }

    @Test
    @DisplayName("하이라이트 저장 성공은 201, Location(/api/highlights/{id}), body는 id 숫자다")
    void saveHighlightIs201WithLocation() throws Exception {
        Member owner = mock(Member.class);
        when(owner.getId()).thenReturn(ME);
        Content parent = mock(Content.class);
        when(parent.getMember()).thenReturn(owner);
        when(contentRepository.findById(1L)).thenReturn(Optional.of(parent));
        Highlight saved = mock(Highlight.class);
        when(saved.getId()).thenReturn(55L);
        when(highlightRepository.save(any(Highlight.class))).thenReturn(saved);

        mockMvc.perform(post("/api/highlights").contentType(MediaType.APPLICATION_JSON)
                .content(HIGHLIGHT_BODY.formatted(1L)).with(TestAuth.member(ME)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/highlights/55"))
                .andExpect(content().string("55"));
    }
}
