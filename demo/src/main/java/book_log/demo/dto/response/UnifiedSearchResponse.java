package book_log.demo.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import book_log.demo.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter @Builder(toBuilder = true) // toBuilder: 캐시된 객체를 건드리지 않고 isSaved만 바꾼 복사본을 만들기 위해 사용
@NoArgsConstructor @AllArgsConstructor
public class UnifiedSearchResponse {
    // 외부 API 기준 고유 ID (책: ISBN13, 영화/드라마/애니: TMDB id). 저장 여부 판별과 '기록하기' 폼 전달에 사용
    private String externalId;
    private String title;
    private String authorOrDirector; // 책: 저자 / 영화: 상세페이지 참조(또는 빈값)
    private String releaseDate;
    private Double voteAverage;
    private String thumbnailUrl;
    private Category category;

    // 우리 DB에 이미 저장된 콘텐츠인지 여부.
    // 디폴트는 null, SearchService를 거친 후에 true/false 됨
    // 캐시(10분 TTL)에 들어가면 저장/삭제 직후에도 낡은 값이 보이기 때문.
    private Boolean isSaved;
}