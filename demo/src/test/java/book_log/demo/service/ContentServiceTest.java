package book_log.demo.service;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.repository.ContentRepository;
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
class ContentServiceTest {
    
    @Mock
    private ContentRepository contentRepository; 

    @InjectMocks
    private ContentService contentService;

    @Test
    @DisplayName("콘텐츠 저장 성공 - 중복이 없는 경우")
    void saveContent_Success() {
        // given
        Content content = Content.builder()
                .title("테스트 영화")
                .externalId("movie_123")
                .category(Category.MOVIE)
                .rating(4.5)
                .build();

        // 가짜 레포지토리의 동작 정의 (Stubbing)
        // 중복 조회하면 비어있다고(Optional.empty) 응답
        when(contentRepository.findByExternalIdAndCategory(any(), any())).thenReturn(Optional.empty());
        // 저장하면 ID가 100L인 객체 반환
        Content savedContent = Content.builder().id(100L).build();
        when(contentRepository.save(any())).thenReturn(savedContent);

        // when
        Long savedId = contentService.saveContent(content);

        // then
        assertEquals(100L, savedId);
        verify(contentRepository, times(1)).save(any()); // 진짜로 저장 메서드가 1번 호출됐는지 확인

    }

    @Test
    @DisplayName("콘텐츠 저장 실패 - 이미 등록된 경우")
    void savedContent_Fail_Duplicate() {
        // given
        Content content = Content.builder().externalId("movie_1").category(Category.MOVIE).build();

        when(contentRepository.findByExternalIdAndCategory(any(), any()))
                .thenReturn(Optional.of(content));

        // when & then
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> {
            contentService.saveContent(content);
        });

        assertEquals("이미 등록된 콘텐츠입니다.", e.getMessage());
    }

    @Test
    @DisplayName("콘텐츠 저장 실패 - 평점 범위를 벗어난 경우")
    void saveContent_Fail_Rating() {
        // given
        Content content = Content.builder()
                .externalId("movie_1")
                .category(Category.MOVIE)
                .rating(10.0)
                .build();

        // when & then
        assertThrows(IllegalArgumentException.class, () -> {
            contentService.saveContent(content);
        });
    }

}
