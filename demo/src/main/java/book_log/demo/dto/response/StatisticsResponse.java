package book_log.demo.dto.response;

import java.util.List;

import lombok.*;

@Getter
@Builder
public class StatisticsResponse {
    private String targetMonth; // 조회 대상

    private List<CategoryStat> categoryStats; // 카테고리별 개수
    private List<GenreStat> genreStats; // 장르별 개수

    private Double averageRating; // 해당 월 평균 별점
    private ActivityComparison activity; // 전월 대비 활동량 비교
    private String mostActiveDay; // "Monday", "Sunday" 등
    private String topCategory; // 이번 달 가장 많이 기록한 카테고리
    private String tasteAnalysis; // "이번 달은 영화보다 도서에 더 몰입하셨네요!" 간단한 메시지

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStat {
        private String category;
        private Long count;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenreStat {
        private String genre;
        private Long count;
    }

    @Getter
    @Builder
    public static class ActivityComparison {
        private long currentCount;
        private long previousCount;
        private double growthRate; // (현월 - 전월) / 전월 * 100
    }
}
