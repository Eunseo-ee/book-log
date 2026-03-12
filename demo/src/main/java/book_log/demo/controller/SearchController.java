package book_log.demo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import book_log.demo.domain.Category;
import book_log.demo.dto.response.UnifiedSearchResponse;
import book_log.demo.service.SearchService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor

public class SearchController {
    private final SearchService searchService;

    /**
     * 통합 검색 API
     * @param category : BOOK, MOVIE, ANIME 등 (String에서 Enum으로 자동 변환됨)
     * @param query    : 검색어
     */

    @GetMapping
    public ResponseEntity<List<UnifiedSearchResponse>> search(
            @RequestParam Category category, // BOOK, MOVIE, ANIME
            @RequestParam String query) {
        
        List<UnifiedSearchResponse> results = searchService.search(category, query);
        return ResponseEntity.ok(results); // HTTP 200 OK와 함께 검색 결과 리스트 JSON 형태로 포장해서 내보냄
    }
}
