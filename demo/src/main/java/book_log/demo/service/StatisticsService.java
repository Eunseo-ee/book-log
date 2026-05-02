package book_log.demo.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import book_log.demo.dto.response.StatisticsResponse;
import book_log.demo.dto.response.StatisticsResponse.CategoryStat;
import book_log.demo.dto.response.StatisticsResponse.GenreStat;
import book_log.demo.repository.ContentRepository;
import book_log.demo.repository.ContentRepository.CategoryStatInterface;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatisticsService {
    
    private final ContentRepository contentRepository;

    @Transactional(readOnly = true)
    public StatisticsResponse getMonthlyStatistics(int year, int month) {
        // 1. 날짜 문자열 생성 (YYYY-MM)
        String currentMonth = String.format("%04d-%02d", year, month);

        // 2. 전월 계산
        LocalDate targetDate = LocalDate.of(year, month, 1);
        String previousMonth = targetDate.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // 3. 데이터 조회 (Interface Projection 사용)
        List<ContentRepository.CategoryStatInterface> categoryResults = contentRepository.countByCategoryInterface(currentMonth);
        List<ContentRepository.GenreStatInterface> genreResults = contentRepository.countByGenreInterface(currentMonth);

        // 4. Interface 결과를 DTO로 변환 (Stream API 활용)
        List<CategoryStat> categoryStats = categoryResults.stream()
                .map(r -> new CategoryStat(r.getCategory(), r.getCount()))
                .toList();

        List<GenreStat> genreStats = genreResults.stream()
                .map(r -> new GenreStat(r.getGenre(), r.getCount()))
                .toList();

        // 5. 평균 별점 가공
        Double rawAvgRating = contentRepository.getAverageRating(currentMonth);
        double averageRating = (rawAvgRating != null) ? Math.round(rawAvgRating * 10) / 10.0 : 0.0;

        // 6. 활동량 비교
        long currentCount = contentRepository.countByMonth(currentMonth);
        long previousCount = contentRepository.countByMonth(previousMonth);
        double growthRate = calculateGrowthRate(currentCount, previousCount);

        // 7. topCategory 추출
        String topCategory = categoryStats.isEmpty() ? "기록 없음" : categoryStats.get(0).getCategory();

        // 8. mostActiveDay 가공
        String mostActiveDay = "기록 없음";
        Object[] dayResult = contentRepository.findMostActiveDay(currentMonth);

        if (dayResult != null && dayResult.length > 0 && dayResult[0] != null) {
            // PostgreSQL DOW: 0(일) ~ 6(토)
            // nativeQuery 결과는 [ [dow, count] ] 형태의 2차원 배열로 올 수 있음
            Object rowObj = dayResult[0];
            if (rowObj instanceof Object[] row) {
                int dow = ((Number) row[0]).intValue();
                mostActiveDay = convertDowToKorean(dow);
            }
        }

        // 9. tasteAnalysis 메시지 생성
        String tasteAnalysis = generateTasteAnalysis(topCategory, categoryStats);

        // 결과 조립
        return StatisticsResponse.builder()
                .targetMonth(currentMonth)
                .categoryStats(categoryStats)
                .topCategory(topCategory)
                .mostActiveDay(mostActiveDay)
                .tasteAnalysis(tasteAnalysis)
                .genreStats(genreStats)
                .averageRating(averageRating)
                .activity(StatisticsResponse.ActivityComparison.builder()
                        .currentCount(currentCount)
                        .previousCount(previousCount)
                        .growthRate(growthRate)
                        .build())
                .build();
    }

    private double calculateGrowthRate(long current, long previous) {
        if (previous == 0) return current > 0 ? 100.0 : 0.0;
        double rate = ((double) (current - previous) / previous) * 100;
        return Math.round(rate * 10) / 10.0;
    }

    private String convertDowToKorean(int dow) {
        String[] days = {"일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일"};
        return days[dow];
    }

    private String generateTasteAnalysis(String topCategory, List<CategoryStat> stats) {
        // 1. 데이터가 아예 없는 경우
        if (stats == null || stats.isEmpty()) {
            return "기록을 시작하여 취향을 분석해 보세요!";
        }

        // 2. 데이터가 2종류 이상일 때 공동 1위 여부 체크
        if (stats.size() >= 2) {
            // Interface가 아니라 DTO인 CategoryStat을 사용합니다.
            CategoryStat first = stats.get(0);
            CategoryStat second = stats.get(1);

            // CategoryStat DTO의 필드명(count)과 게터(getCount())를 사용합니다.
            if (!first.getCount().equals(second.getCount())) {
                return "이번 달은 " + second.getCategory() + "보다 " + first.getCategory() + "에 더 몰입하셨네요!";
            }
        }

        // 3. 데이터가 1종류뿐이거나, 공동 1위인 경우
        return "이번 달은 " + topCategory + "에 집중하신 한 달이었군요!";
    }
}