package book_log.demo.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import book_log.demo.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
public class UnifiedSearchResponse {
    private String title;
    private String authorOrDirector; // 책: 저자 / 영화: 상세페이지 참조(또는 빈값)
    private String releaseDate;
    private Double voteAverage;
    private String thumbnailUrl;
    private Category category;
}