package book_log.demo.service;

import book_log.demo.domain.Content;
import book_log.demo.domain.Highlight;
import book_log.demo.domain.Member;
import book_log.demo.exception.ForbiddenResourceException;
import book_log.demo.exception.ResourceNotFoundException;
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

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;

    private static Content contentOf(Long contentId, Long ownerId) {
        return Content.builder().id(contentId).title("테스트 작품")
                .member(Member.builder().id(ownerId).build()).build();
    }

    private static Highlight highlightOf(Long id, Content content) {
        return Highlight.builder().id(id).text("원래 문구").content(content).build();
    }

    private static HighlightRequestDto updateDto(Long contentId) {
        return HighlightRequestDto.builder().contentId(contentId).text("바뀐 문구").page(7).build();
    }

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

        Content content = contentOf(contentId, ME);

        // 콘텐츠 조회 시 가짜 콘텐츠 반환 설정
        when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
        // 저장 시 가짜 하이라이트 엔티티 반환 설정
        // 어떤 Highlight 객체가 들어오든 save 메서드가 실행되면 들어온 내용을 복사하되 ID는 500L로 반환
        when(highlightRepository.save(any(Highlight.class))).thenAnswer(inv -> {
            Highlight h = inv.getArgument(0);
            return Highlight.builder().id(500L).text(h.getText()).build();
        });

        // when
        Long savedId = highlightService.saveHighlight(ME, requestDto);

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
        assertThrows(IllegalArgumentException.class, () -> highlightService.saveHighlight(ME, requestDto));
        verify(highlightRepository, never()).save(any()); // 저장이 절대 호출되면 안 됨
    }

    @Test
    @DisplayName("하이라이트 저장 실패 - 다른 회원의 콘텐츠면 403 예외, 저장 안 됨")
    void saveHighlight_Fail_NotOwner() {
        HighlightRequestDto requestDto = HighlightRequestDto.builder()
                .contentId(1L).text("남의 작품에 다는 문구").build();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(contentOf(1L, OTHER)));

        assertThrows(ForbiddenResourceException.class, () -> highlightService.saveHighlight(ME, requestDto));
        verify(highlightRepository, never()).save(any());
    }

    @Test
    @DisplayName("하이라이트 수정 성공 - 내 하이라이트")
    void updateHighlight_Success() {
        Highlight highlight = highlightOf(10L, contentOf(1L, ME));
        when(highlightRepository.findById(10L)).thenReturn(Optional.of(highlight));

        Long result = highlightService.updateHighlight(ME, 10L, updateDto(1L));

        assertEquals(10L, result);
        assertEquals("바뀐 문구", highlight.getText());
        assertEquals(7, highlight.getPage());
    }

    @Test
    @DisplayName("하이라이트 수정 시 요청의 contentId가 달라도 다른 콘텐츠로 옮겨가지 않는다")
    void updateHighlight_DoesNotMoveContent() {
        Content original = contentOf(1L, ME);
        Highlight highlight = highlightOf(10L, original);
        when(highlightRepository.findById(10L)).thenReturn(Optional.of(highlight));

        highlightService.updateHighlight(ME, 10L, updateDto(999L));

        assertSame(original, highlight.getContent());
        verify(contentRepository, never()).findById(any());
    }

    @Test
    @DisplayName("하이라이트 수정 실패 - 다른 회원의 하이라이트면 403 예외, 내용 그대로")
    void updateHighlight_Fail_NotOwner() {
        Highlight highlight = highlightOf(10L, contentOf(1L, OTHER));
        when(highlightRepository.findById(10L)).thenReturn(Optional.of(highlight));

        assertThrows(ForbiddenResourceException.class,
                () -> highlightService.updateHighlight(ME, 10L, updateDto(1L)));
        assertEquals("원래 문구", highlight.getText());
    }

    @Test
    @DisplayName("하이라이트 수정 실패 - 없는 id면 404 예외")
    void updateHighlight_Fail_NotFound() {
        when(highlightRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> highlightService.updateHighlight(ME, 404L, updateDto(1L)));
    }

    @Test
    @DisplayName("하이라이트 삭제 성공 - 내 하이라이트")
    void deleteHighlight_Success() {
        Highlight highlight = highlightOf(10L, contentOf(1L, ME));
        when(highlightRepository.findById(10L)).thenReturn(Optional.of(highlight));

        highlightService.deleteHighlight(ME, 10L);

        verify(highlightRepository).delete(highlight);
    }

    @Test
    @DisplayName("하이라이트 삭제 실패 - 다른 회원의 하이라이트면 403 예외, 삭제 안 됨")
    void deleteHighlight_Fail_NotOwner() {
        when(highlightRepository.findById(10L))
                .thenReturn(Optional.of(highlightOf(10L, contentOf(1L, OTHER))));

        assertThrows(ForbiddenResourceException.class, () -> highlightService.deleteHighlight(ME, 10L));
        verify(highlightRepository, never()).delete(any(Highlight.class));
        verify(highlightRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("하이라이트 삭제 실패 - 없는 id면 404 예외, 삭제 안 됨")
    void deleteHighlight_Fail_NotFound() {
        when(highlightRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> highlightService.deleteHighlight(ME, 404L));
        verify(highlightRepository, never()).delete(any(Highlight.class));
    }

    @Test
    @DisplayName("하이라이트 텍스트가 너무 길면 저장에 실패해야 한다")
    void saveHighlight_Fail_TooLongText() {
        // given: 1000자 이상의 긴 문자열 생성
        String longText = "a".repeat(1001);
        HighlightRequestDto requestDto = HighlightRequestDto.builder()
                .contentId(1L)
                .text(longText)
                .build();

        // when & then
        assertThrows(IllegalArgumentException.class, () -> highlightService.saveHighlight(ME, requestDto));
    }
}
