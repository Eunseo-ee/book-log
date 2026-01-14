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
    private String thumbnailUrl;
    private String externalId;
    private String authorOrDirector;
    // 우리가 정해놓은 Category Enuim에 맞게 내보내도록
    private Category category;
}
