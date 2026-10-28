package book_log.demo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import book_log.demo.domain.Category;
import book_log.demo.dto.response.SearchResponse;
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
    public ResponseEntity<SearchResponse> search(
            @RequestParam Category category, // BOOK, MOVIE, ANIME
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page) {
        
        // 임시로 회원 ID를 1L로 설정 (인증 연동 시 교체, task 19)
        Long memberId = 1L;
        return ResponseEntity.ok(searchService.search(memberId, category, query, page));
    }
}
