package book_log.demo.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.BDDMockito.given;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import book_log.demo.dto.response.CalendarResponse;
import book_log.demo.repository.ContentRepository;

@ExtendWith(MockitoExtension.class)
public class CalendarServiceTest {
    
    @InjectMocks
    private CalendarService calendarService;

    @Mock
    private ContentRepository contentRepository;

    @Test
    @DisplayName("조회된 날짜 리스트를 CalendarResponse DTO로 변환")
    void getMonthlyActivityTest() {
        // given
        Long userId = 1L;
        int year = 2026;
        int month = 4;

        // Mock 객체의 동작 정의
        given(contentRepository.findActiveDays(userId, year, month))
                .willReturn(List.of(4,10,15));

        // when (실제 서비스 호출)
        CalendarResponse response = calendarService.getMonthlyActivity(userId, year, month);

        // then (검증)
        assertThat(response.year()).isEqualTo(year);
        assertThat(response.month()).isEqualTo(month);
        assertThat(response.days()).hasSize(3);
        assertThat(response.days().get(0).day()).isEqualTo(4);
    }

    @Test
    @DisplayName("기록이 없는 달을 조회하면 빈 리스트를 포함한 DTO 반환")
    void getMonthlyActivityEmptyTest() {
        //given
        Long userId = 1L;
        int year = 2026;
        int month = 1; // 기록이 없는 달 가정

        given(contentRepository.findActiveDays(userId, year, month))
                .willReturn(List.of()); // 빈 리스트 반환

        // when
        CalendarResponse response = calendarService.getMonthlyActivity(userId, year, month);

        // then
        assertThat(response.days()).isEmpty();
        assertThat(response.year()).isEqualTo(year);
    }

    @Test
    @DisplayName("리스트의 모든 날짜가 정확하게 DayActivity로 매핑된다")
    void getMonthlyActivityMappingTest() {
        //given
        List<Integer> mockDays = List.of(1,15,30);

        given(contentRepository.findActiveDays(1L, 2026, 4))
                .willReturn(mockDays);

        // when
        CalendarResponse response = calendarService.getMonthlyActivity(1L, 2026, 4);

        // then
        // 리스트 내의 day 값들만 추출해서 비교
        List<Integer> actualDays = response.days().stream()
                .map(CalendarResponse.DayActivity::day)
                .toList();

        assertThat(actualDays).containsExactly(1,15,30);
    }

}
