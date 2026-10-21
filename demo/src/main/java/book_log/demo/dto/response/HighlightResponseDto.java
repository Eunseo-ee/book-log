package book_log.demo.dto.response;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.domain.Highlight;

import java.time.LocalDateTime;

// 하이라이트 모음집 목록 응답 (부모 콘텐츠 정보는 평평하게 펼쳐서 내려준다)
public record HighlightResponseDto(
    Long id,
    String text,
    Integer page,
    Integer season,
    Integer episode,
    String timestamp,
    LocalDateTime createdAt,
    Long contentId,
    String contentTitle,
    Category contentCategory
) {
    public static HighlightResponseDto from(Highlight highlight) {
        Content content = highlight.getContent();
        return new HighlightResponseDto(
            highlight.getId(),
            highlight.getText(),
            highlight.getPage(),
            highlight.getSeason(),
            highlight.getEpisode(),
            highlight.getTimestamp(),
            highlight.getCreatedAt(),
            content != null ? content.getId() : null,
            content != null ? content.getTitle() : null,
            content != null ? content.getCategory() : null
        );
    }
}
