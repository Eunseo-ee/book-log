package book_log.demo.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import book_log.demo.config.SecurityConfig;
import book_log.demo.security.JwtTokenProvider;
import book_log.demo.security.TestAuth;
import book_log.demo.service.StatisticsService;

// month/year 기본값과 범위 검증 순서 (task 12 발견 8번: month가 null이면 NPE였다)
@WebMvcTest(StatisticsController.class)
@Import({ SecurityConfig.class, StatisticsControllerTest.FixedClockConfig.class })
class StatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // 시간을 테스트가 고정하기 위한 가변 Clock. 서비스가 보는 "지금"은 항상 이 시계 (zone은 운영 설정과 같은 KST)
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static volatile Instant instantNow = Instant.parse("2026-05-31T14:59:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return new Clock() {
                @Override public ZoneId getZone() { return KST; }
                @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instantNow, zone); }
                @Override public Instant instant() { return instantNow; }
            };
        }
    }

    @MockitoBean
    private StatisticsService statisticsService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("year/month 둘 다 없으면 현재 연/월로 조회한다")
    void bothMissingUsesCurrentMonth() throws Exception {
        LocalDate now = LocalDate.now(Clock.fixed(instantNow, KST));

        mockMvc.perform(get("/api/statistics").with(TestAuth.member(7L)))
                .andExpect(status().isOk());

        verify(statisticsService).getMonthlyStatistics(7L, now.getYear(), now.getMonthValue());
    }

    @Test
    @DisplayName("month만 없어도 NPE 없이 200이고, 그 해의 현재 월로 조회한다")
    void monthMissingIsNotNpe() throws Exception {
        mockMvc.perform(get("/api/statistics?year=2025").with(TestAuth.member(7L)))
                .andExpect(status().isOk());

        verify(statisticsService).getMonthlyStatistics(7L, 2025, LocalDate.now(Clock.fixed(instantNow, KST)).getMonthValue());
    }

    @Test
    @DisplayName("year만 없으면 올해의 지정한 월로 조회한다")
    void yearMissingUsesCurrentYear() throws Exception {
        mockMvc.perform(get("/api/statistics?month=3").with(TestAuth.member(7L)))
                .andExpect(status().isOk());

        verify(statisticsService).getMonthlyStatistics(7L, LocalDate.now(Clock.fixed(instantNow, KST)).getYear(), 3);
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

    // KST 경계: 아래 두 시각은 UTC 날짜와 KST 날짜가 다르다 (UTC는 5/31, KST는 6/1이 되는 순간 전후)
    @Test
    @DisplayName("KST 5/31 23:59(UTC 5/31 14:59)에는 기본값이 5월이다")
    void defaultsAreMayJustBeforeKstMidnight() throws Exception {
        instantNow = Instant.parse("2026-05-31T14:59:00Z");

        mockMvc.perform(get("/api/statistics").with(TestAuth.member(7L)))
                .andExpect(status().isOk());

        verify(statisticsService).getMonthlyStatistics(7L, 2026, 5);
    }

    @Test
    @DisplayName("KST 6/1 00:01(UTC 5/31 15:01)에는 UTC 날짜는 아직 5월이어도 기본값이 6월이다")
    void defaultsAreJuneJustAfterKstMidnight() throws Exception {
        instantNow = Instant.parse("2026-05-31T15:01:00Z");
        // UTC 기준 날짜는 5/31 (서버가 UTC 기본 시간대였다면 5월이 나왔을 값)
        org.junit.jupiter.api.Assertions.assertEquals(5, LocalDate.ofInstant(instantNow, ZoneOffset.UTC).getMonthValue());

        mockMvc.perform(get("/api/statistics").with(TestAuth.member(7L)))
                .andExpect(status().isOk());

        verify(statisticsService).getMonthlyStatistics(7L, 2026, 6);
    }
}
