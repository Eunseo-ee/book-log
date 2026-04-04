package book_log.demo.service;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import book_log.demo.domain.Content;
import book_log.demo.domain.Highlight;
import book_log.demo.dto.request.HighlightRequestDto;
import book_log.demo.repository.ContentRepository;
import book_log.demo.repository.HighlightRepository;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HighlightService {
    
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

}
