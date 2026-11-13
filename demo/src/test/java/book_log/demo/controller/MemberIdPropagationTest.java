package book_log.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import book_log.demo.config.TimeConfig;
import book_log.demo.domain.Category;
import book_log.demo.security.JwtTokenProvider;
import book_log.demo.security.TestAuth;
import book_log.demo.service.CalendarService;
import book_log.demo.service.ContentService;
import book_log.demo.service.HighlightService;
import book_log.demo.service.SearchService;
import book_log.demo.service.StatisticsService;

// 로그인 사용자의 id(7)가 하드코딩 1L이 아니라 서비스 호출 인자로 그대로 넘어가는지 확인한다
@WebMvcTest(controllers = { CalendarController.class, ContentController.class, HighlightController.class,
        SearchController.class, StatisticsController.class })
@Import({ SecurityConfig.class, TimeConfig.class })
class MemberIdPropagationTest {

    private static final long ME = 7L;

    @Autowired
    private MockMvc mockMvc;

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
    // SecurityConfig가 JWT 필터를 만들 때 필요하다 (여기서는 토큰 대신 인증 객체를 직접 심는다)
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("캘린더: 로그인 사용자 id가 서비스로 전달된다")
    void calendar() throws Exception {
        mockMvc.perform(get("/api/calendar/activities?year=2026&month=4").with(TestAuth.member(ME)))
                .andExpect(status().isOk());

        verify(calendarService).getMonthlyActivity(ME, 2026, 4);
    }

    @Test
    @DisplayName("통계: 로그인 사용자 id가 서비스로 전달된다")
    void statistics() throws Exception {
        mockMvc.perform(get("/api/statistics?year=2026&month=4").with(TestAuth.member(ME)))
                .andExpect(status().isOk());

        verify(statisticsService).getMonthlyStatistics(ME, 2026, 4);
    }

    @Test
    @DisplayName("검색: 로그인 사용자 id가 서비스로 전달된다")
    void search() throws Exception {
        mockMvc.perform(get("/api/search?category=BOOK&query=abc&page=2").with(TestAuth.member(ME)))
                .andExpect(status().isOk());

        verify(searchService).search(ME, Category.BOOK, "abc", 2);
    }

    @Test
    @DisplayName("콘텐츠: 저장/중복 확인/필터 조회 모두 로그인 사용자 id를 넘긴다")
    void content() throws Exception {
        when(contentService.saveContent(eq(ME), any())).thenReturn(100L);

        mockMvc.perform(post("/api/contents").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"제목\",\"viewDate\":\"2026-04-01\",\"category\":\"BOOK\"}")
                .with(TestAuth.member(ME)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/contents/check?externalId=ext1&category=BOOK").with(TestAuth.member(ME)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/contents/filter?year=2026&category=BOOK").with(TestAuth.member(ME)))
                .andExpect(status().isOk());

        verify(contentService).saveContent(eq(ME), any());
        verify(contentService).isAlreadySaved(ME, "ext1", Category.BOOK);
        verify(contentService).getFilteredContents(ME, 2026, Category.BOOK);
    }

    @Test
    @DisplayName("하이라이트: 조회/저장/수정/삭제 모두 로그인 사용자 id를 넘긴다")
    void highlight() throws Exception {
        String body = "{\"text\":\"문구\",\"contentId\":1}";

        mockMvc.perform(get("/api/highlights?year=2026&category=BOOK").with(TestAuth.member(ME)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/highlights").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(TestAuth.member(ME)))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/highlights/10").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(TestAuth.member(ME)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/highlights/10").with(TestAuth.member(ME)))
                .andExpect(status().isNoContent());

        verify(highlightService).getHighlights(ME, 2026, Category.BOOK);
        verify(highlightService).saveHighlight(eq(ME), any());
        verify(highlightService).updateHighlight(eq(ME), eq(10L), any());
        verify(highlightService).deleteHighlight(ME, 10L);
    }

    @Test
    @DisplayName("다른 사용자로 호출하면 그 사용자의 id가 넘어간다 (1L 고정이 아니다)")
    void differentUsersGetTheirOwnId() throws Exception {
        mockMvc.perform(get("/api/highlights").with(TestAuth.member(1L))).andExpect(status().isOk());
        mockMvc.perform(get("/api/highlights").with(TestAuth.member(2L))).andExpect(status().isOk());

        verify(highlightService).getHighlights(1L, null, null);
        verify(highlightService).getHighlights(2L, null, null);
    }
}
