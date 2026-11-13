package book_log.demo.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import book_log.demo.security.AuthenticatedMember;
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
            @RequestParam(required = false) Category category,
            @AuthenticationPrincipal AuthenticatedMember member) {
        return ResponseEntity.ok(highlightService.getHighlights(member.id(), year, category));
    }

    @PostMapping
    public ResponseEntity<Long> save(@Valid @RequestBody HighlightRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedMember member) {
        Long savedId = highlightService.saveHighlight(member.id(), requestDto);
        // Location은 수정/삭제(PUT/DELETE) 경로를 가리킨다. 단건 GET /api/highlights/{id}는 없다
        return ResponseEntity.created(URI.create("/api/highlights/" + savedId)).body(savedId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Long> update(@PathVariable Long id, @Valid @RequestBody HighlightRequestDto requestDto,
            @AuthenticationPrincipal AuthenticatedMember member) {
        return ResponseEntity.ok(highlightService.updateHighlight(member.id(), id, requestDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedMember member) {
        highlightService.deleteHighlight(member.id(), id);

        return ResponseEntity.noContent().build();
    }

}
