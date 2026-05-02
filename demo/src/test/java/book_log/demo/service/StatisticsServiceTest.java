package book_log.demo.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import book_log.demo.dto.response.StatisticsResponse;
import book_log.demo.repository.ContentRepository;
import book_log.demo.repository.ContentRepository.CategoryStatInterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private ContentRepository contentRepository;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    @DisplayName("데이터가 있는 경우 - 통계 계산이 정확해야 한다")
    void getStatistics_Success() {
        // given
        String targetMonth = "2026-05";
        String prevMonth = "2026-04";

        // 가짜 리스트 데이터 (MOVIE 3개, BOOK 2개)
        List<CategoryStatInterface> categoryStats = List.of(
            createCategoryStat("MOVIE", 3L),
            createCategoryStat("BOOK", 2L)
        );

        when(contentRepository.countByCategoryInterface(targetMonth)).thenReturn(categoryStats);
        when(contentRepository.getAverageRating(targetMonth)).thenReturn(4.666);
        when(contentRepository.countByMonth(targetMonth)).thenReturn(5L);
        when(contentRepository.countByMonth(prevMonth)).thenReturn(10L);
        when(contentRepository.findMostActiveDay(targetMonth)).thenReturn(new Object[]{5, 3L}); // 금요일

        // when
        StatisticsResponse result = statisticsService.getMonthlyStatistics(2026, 5);

        // then
        assertThat(result.getAverageRating()).isEqualTo(4.7); // 반올림 확인
        assertThat(result.getActivity().getGrowthRate()).isEqualTo(-50.0); // 성장률 확인
        assertThat(result.getTopCategory()).isEqualTo("MOVIE");
        assertThat(result.getTasteAnalysis()).contains("BOOK보다 MOVIE에 더 몰입");
    }

    @Test
    @DisplayName("데이터가 없는 경우 - 기본값(기록 없음)을 반환해야 한다")
    void getStatistics_ZeroState() {
        // given
        String targetMonth = "2026-12";
        when(contentRepository.countByCategoryInterface(targetMonth)).thenReturn(List.of());

        // when
        StatisticsResponse result = statisticsService.getMonthlyStatistics(2026, 12);

        // then
        assertThat(result.getTopCategory()).isEqualTo("기록 없음");
        assertThat(result.getAverageRating()).isEqualTo(0.0);
        assertThat(result.getCategoryStats()).isEmpty();
    }

    // 인터페이스 모킹을 위한 헬퍼 메서드
    private CategoryStatInterface createCategoryStat(String category, Long count) {
        return new CategoryStatInterface() {
            @Override public String getCategory() { return category; }
            @Override public Long getCount() { return count; }
        };
    }

    @Test
    @DisplayName("지난달 기록은 있고 이번달 기록은 없을 때 - 성장률 -100% 확인")
    void growthRate_Minus100() {
        // given
        when(contentRepository.countByCategoryInterface(anyString())).thenReturn(List.of());
        when(contentRepository.countByMonth("2026-05")).thenReturn(0L);
        when(contentRepository.countByMonth("2026-04")).thenReturn(10L);

        // when
        StatisticsResponse result = statisticsService.getMonthlyStatistics(2026, 5);

        // then
        assertThat(result.getActivity().getGrowthRate()).isEqualTo(-100.0);
    }

    @Test
    @DisplayName("기록이 한 종류일 때 - 단독형 메시지 확인")
    void tasteAnalysis_SingleCategory() {
        // given
        List<CategoryStatInterface> singleStat = List.of(createCategoryStat("BOOK", 5L));
        when(contentRepository.countByCategoryInterface(anyString())).thenReturn(singleStat);

        // when
        StatisticsResponse result = statisticsService.getMonthlyStatistics(2026, 5);

        // then
        assertThat(result.getTasteAnalysis()).isEqualTo("이번 달은 BOOK에 집중하신 한 달이었군요!");
    }

    @Test
@DisplayName("카테고리별 개수가 동일할 때(공동 1위) - 에러 없이 첫 번째 항목을 topCategory로 선정")
void getStatistics_TieRank() {
    // given (BOOK 3개, MOVIE 3개)
    List<CategoryStatInterface> tieStats = List.of(
        createCategoryStat("BOOK", 3L),
        createCategoryStat("MOVIE", 3L)
    );
    when(contentRepository.countByCategoryInterface(anyString())).thenReturn(tieStats);

    // when
    StatisticsResponse result = statisticsService.getMonthlyStatistics(2026, 5);

    // then
    assertThat(result.getTopCategory()).isIn("BOOK", "MOVIE"); // 둘 중 하나라도 정상 반환되면 통과
    assertThat(result.getTasteAnalysis()).contains("집중하신 한 달"); // 비교 대상이 없으므로 단독형 메시지 출력 여부 확인
}
}