package book_log.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import book_log.demo.dto.response.UnifiedSearchResponse;
import book_log.demo.service.SearchService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor

public class SearchController {
    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<List<UnifiedSearchResponse>> search()
}
