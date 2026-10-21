package book_log.demo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import book_log.demo.domain.Category;
import book_log.demo.dto.request.HighlightRequestDto;
import book_log.demo.dto.response.HighlightResponseDto;
import book_log.demo.service.HighlightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/highlights")
@RequiredArgsConstructor
public class HighlightController {
    
    private final HighlightService highlightService;

    /**
     * 하이라이트 모음집 조회 API
     * GET /api/highlights?year=2026&category=ANIME (둘 다 선택)
     */
    @GetMapping
    public ResponseEntity<List<HighlightResponseDto>> getHighlights(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Category category) {
        return ResponseEntity.ok(highlightService.getHighlights(year, category));
    }

    @PostMapping
    public ResponseEntity<Long> save(@Valid @RequestBody HighlightRequestDto requestDto) {
        return ResponseEntity.ok(highlightService.saveHighlight(requestDto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Long> update(@PathVariable Long id, @Valid @RequestBody HighlightRequestDto requestDto) {
        return ResponseEntity.ok(highlightService.updateHighlight(id, requestDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        highlightService.deleteHighlight(id);

        return ResponseEntity.noContent().build();
    }

}
