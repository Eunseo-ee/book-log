package book_log.demo.service;

import book_log.demo.domain.Content;
import book_log.demo.domain.Highlight;
import book_log.demo.dto.request.HighlightRequestDto;
import book_log.demo.repository.ContentRepository;
import book_log.demo.repository.HighlightRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HighlightServiceTest {
    
    @Mock
    private HighlightRepository highlightRepository;

    @Mock
    private ContentRepository contentRepository;

    @InjectMocks
    private HighlightService highlightService;

    @Test
    @DisplayName("하이라이트 저장 성공 - 연관된 콘텐츠가 있을 때")
    void saveHighlght_Success() {
        //given
        Long contentId = 1L;
        HighlightRequestDto requestDto = HighlightRequestDto.builder()
                .contentId(contentId)
                .text("어떤 일이 있어도 포기하지 마.")
                .page(42)
                .build();

        Content content = Content.builder().id(contentId).title("테스트 작품").build();

        // 콘텐츠 조회 시 가짜 콘텐츠 반환 설정
        when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
        // 저장 시 가짜 하이라이트 엔티티 반환 설정
        // 어떤 Highlight 객체가 들어오든 save 메서드가 실행되면 들어온 내용을 복사하되 ID는 500L로 반환
        when(highlightRepository.save(any(Highlight.class))).thenAnswer(inv -> {
            Highlight h = inv.getArgument(0);
            return Highlight.builder().id(500L).text(h.getText()).build();
        });

        // when
        Long savedId = highlightService.saveHighlight(requestDto);

        // then
        assertEquals(500L, savedId);
        verify(contentRepository).findById(contentId);
        verify(highlightRepository).save(any(Highlight.class));
    }

    @Test
    @DisplayName("하이라이트 저장 실패 - 콘텐츠가 존재하지 않을 때")
    void saveHighlight_Fail_NoContent() {
        // given
        HighlightRequestDto requestDto = HighlightRequestDto.builder()
                .contentId(999L)
                .text("기록되지 못할 문구")
                .build();

        when(contentRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThrows(IllegalArgumentException.class, () -> highlightService.saveHighlight(requestDto));
        verify(highlightRepository, never()).save(any()); // 저장이 절대 호출되면 안 됨
    }

    @Test
    @DisplayName("하이라이트 삭제 성공")
    void deleteHighlight_Success() {
        //given
        Long highlightId = 10L;

        // when
        highlightService.deleteHighlight(highlightId);

        // then
        verify(highlightRepository).deleteById(highlightId);
    }

}
