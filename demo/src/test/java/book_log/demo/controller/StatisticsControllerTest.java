package book_log.demo.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import book_log.demo.config.SecurityConfig;
import book_log.demo.security.JwtTokenProvider;
import book_log.demo.security.TestAuth;
import book_log.demo.service.StatisticsService;

// month/year 기본값과 범위 검증 순서 (task 12 발견 8번: month가 null이면 NPE였다)
@WebMvcTest(StatisticsController.class)
@Import(SecurityConfig.class)
class StatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatisticsService statisticsService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("year/month 둘 다 없으면 현재 연/월로 조회한다")
    void bothMissingUsesCurrentMonth() throws Exception {
        LocalDate now = LocalDate.now();

        mockMvc.perform(get("/api/statistics").with(TestAuth.member(7L)))
                .andExpect(status().isOk());

        verify(statisticsService).getMonthlyStatistics(7L, now.getYear(), now.getMonthValue());
    }

    @Test
    @DisplayName("month만 없어도 NPE 없이 200이고, 그 해의 현재 월로 조회한다")
    void monthMissingIsNotNpe() throws Exception {
        mockMvc.perform(get("/api/statistics?year=2025").with(TestAuth.member(7L)))
                .andExpect(status().isOk());

        verify(statisticsService).getMonthlyStatistics(7L, 2025, LocalDate.now().getMonthValue());
    }

    @Test
    @DisplayName("year만 없으면 올해의 지정한 월로 조회한다")
    void yearMissingUsesCurrentYear() throws Exception {
        mockMvc.perform(get("/api/statistics?month=3").with(TestAuth.member(7L)))
                .andExpect(status().isOk());

        verify(statisticsService).getMonthlyStatistics(7L, LocalDate.now().getYear(), 3);
    }

    @ParameterizedTest(name = "month={0}")
    @ValueSource(strings = { "0", "13", "-1" })
    @DisplayName("month가 1~12 밖이면 400과 ErrorResponse이고 서비스는 호출되지 않는다")
    void outOfRangeMonthIs400(String month) throws Exception {
        mockMvc.perform(get("/api/statistics?year=2026&month=" + month).with(TestAuth.member(7L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(statisticsService, never()).getMonthlyStatistics(anyLong(), anyInt(), anyInt());
    }
}
