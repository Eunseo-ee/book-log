package book_log.demo.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class SearchResponse {
    private List<UnifiedSearchResponse> results;
    private int currentPage;
    private int totalPages;
    private long totalResults;
}
