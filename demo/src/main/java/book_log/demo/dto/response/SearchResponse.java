package book_log.demo.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SearchResponse {
    private List<UnifiedSearchResponse> results;
    private int currentPage;
    private int totalPages;
    private long totalResults;
}
