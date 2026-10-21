package book_log.demo.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.domain.Highlight;
import book_log.demo.dto.request.HighlightRequestDto;
import book_log.demo.dto.response.HighlightResponseDto;
import book_log.demo.repository.ContentRepository;
import book_log.demo.repository.HighlightRepository;
import book_log.demo.repository.HighlightSpecifications;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HighlightService {

    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2100;

    private final HighlightRepository highlightRepository;
    private final ContentRepository contentRepository;

    @Transactional
    public Long saveHighlight(HighlightRequestDto requestDto) {
        
        // 1. 부모 콘텐츠 찾기 (없으면 에러)
        Content content = contentRepository.findById(requestDto.getContentId()).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다. id=" + requestDto.getContentId()));

        // 2. 하이라이트 엔티티 생성
        Highlight highlight = Highlight.builder()
                .text(requestDto.getText())
                .page(requestDto.getPage())
                .season(requestDto.getSeason())
                .episode(requestDto.getEpisode())
                .timestamp(requestDto.getTimestamp())
                .content(content) // 연관관계 설정
                .build();

        // 3. 저장
        return highlightRepository.save(highlight).getId();

    }

    @Transactional
    public Long updateHighlight(Long id, HighlightRequestDto requestDto) {
        Highlight highlight = highlightRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 하이라이트가 없습니다. id=" + id));

        highlight.update(
                requestDto.getText(),
                requestDto.getPage(),
                requestDto.getSeason(),
                requestDto.getEpisode(),
                requestDto.getTimestamp()

        );

        return id;
    }

    @Transactional
    public void deleteHighlight(Long id) {
        highlightRepository.deleteById(id);
    }

    // 모음집 목록 조회: year(Content.viewDate 기준), category 모두 선택
    public List<HighlightResponseDto> getHighlights(Integer year, Category category) {
        if (year != null && (year < MIN_YEAR || year > MAX_YEAR)) {
            throw new IllegalArgumentException("연도는 " + MIN_YEAR + "~" + MAX_YEAR + " 사이여야 합니다. year=" + year);
        }

        Specification<Highlight> spec = Specification
                .where(HighlightSpecifications.viewYear(year))
                .and(HighlightSpecifications.categoryIn(resolveCategories(category)));
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        return highlightRepository.findAll(spec, sort).stream()
                .map(HighlightResponseDto::from)
                .toList();
    }

    // null/ALL은 필터 없음(null), ANIME는 TMDB 애니 카테고리까지 묶음, 나머지는 단일
    static List<Category> resolveCategories(Category category) {
        if (category == null || category == Category.ALL) {
            return null;
        }
        if (category == Category.ANIME) {
            return List.of(Category.ANIME, Category.ANIME_MOVIE, Category.ANIME_TVA);
        }
        return List.of(category);
    }

}
