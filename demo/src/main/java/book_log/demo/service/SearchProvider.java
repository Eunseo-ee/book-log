package book_log.demo.service;

import java.util.List;

import book_log.demo.domain.Category;
import book_log.demo.dto.response.UnifiedSearchResponse;

public interface SearchProvider {
    List<UnifiedSearchResponse> search(String query);
    boolean supports(Category category);
}
