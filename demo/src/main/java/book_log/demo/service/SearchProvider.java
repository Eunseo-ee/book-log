package book_log.demo.service;

import java.util.List;

import book_log.demo.domain.Category;
import book_log.demo.dto.response.UnifiedSearchResponse;

public interface SearchProvider {

    // SearchService가 Category 물었을때 각 서비스가 대답하는 기준
    boolean supports(Category category);

    // 외부 API의 검색 결과를 우리 시스템의 공통 규격인 UnifiedSearchResponse로 변환하여 반환하겠다는 약속
    List<UnifiedSearchResponse> search(Category category, String query);
}
