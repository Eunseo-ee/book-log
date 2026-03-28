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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {
    
    @Mock
    private ContentRepository contentRepository; 

    @InjectMocks
    private ContentService contentService;

    @Test
    @DisplayName("콘텐츠 저장 성공 - 데이터 변환 및 매핑 확인")
    void saveContent_Success() {
        // given
        Content content = Content.builder()
                .title("테스트 영화")
                .externalId("movie_123")
                .category(Category.MOVIE)
                .rating(4.5)
                .build();

        //중요 : 매핑이 잘 되었는지 확인하기 위해 입력받은 content 그대로 반환하도록 설정
        when(contentRepository.save(any(Content.class))).thenAnswer(invocation -> {
            Content argument = invocation.getArgument(0);
            // 여기서 ID만 강제로 세팅해서 반환 (실제 DB가 하는 일 흉내)
            return Content.builder()
                    .id(100L)
                    .title(argument.getTitle())
                    .externalId(argument.getExternalId())
                    .category(argument.getCategory())
                    .build();
        });

        //when
        Long savedId = contentService.saveContent(content);

        //then
        assertEquals(100L, savedId);
        // 저장 시점에 데이터가 유실되지 않고 잘 전달됐는지 확인
        verify(contentRepository).save(argThat(c -> 
            c.getTitle().equals("테스트 영화") &&
            c.getExternalId().equals("movie_123")
        ));
    }

    @Test
    @DisplayName("콘텐츠 저장 실패 - 이미 등록된 경우")
    void savedContent_Fail_Duplicate() {
        // given
        Content content = Content.builder().externalId("movie_1").category(Category.MOVIE).build();

        when(contentRepository.findByExternalIdAndCategory(any(), any()))
                .thenReturn(Optional.of(content));

        // when & then
        assertThrows(IllegalStateException.class, () -> contentService.saveContent(content));
    }

    @Test
    @DisplayName("경계값 테스트: 평점이 0 미만인 경우 실패")
    void saveContent_Fail_Rating_Low() {
        // given: 별점이 -1.0인 경우
        Content content = Content.builder().rating(-1.0).build();

        // when & then
        assertThrows(IllegalArgumentException.class, () -> contentService.saveContent(content));
    }

    @Test
    @DisplayName("콘텐츠 저장 실패 - 평점 범위를 초과한 경우")
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
