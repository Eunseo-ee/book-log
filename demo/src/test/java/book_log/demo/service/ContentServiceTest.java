package book_log.demo.service;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.dto.request.ContentRequestDto;
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
        // given: Content 엔티티 대신 RequestDto를 생성합니다.
        ContentRequestDto requestDto = ContentRequestDto.builder()
                .title("테스트 영화")
                .externalId("movie_123")
                .category(Category.MOVIE)
                .rating(4.5)
                .build();

        // 중요 : 서비스 내부에서 toEntity()가 호출되어 repository.save()로 넘어갑니다.
        when(contentRepository.save(any(Content.class))).thenAnswer(invocation -> {
            Content argument = invocation.getArgument(0);
            // 실제 DB가 ID를 부여하는 과정을 흉내냅니다.
            return Content.builder()
                    .id(100L)
                    .title(argument.getTitle())
                    .externalId(argument.getExternalId())
                    .category(argument.getCategory())
                    .build();
        });

        // when: 이제 DTO를 파라미터로 넘깁니다.
        Long savedId = contentService.saveContent(requestDto);

        // then
        assertEquals(100L, savedId);
        // 저장 시점에 데이터가 유실되지 않고 엔티티로 잘 변환되어 전달됐는지 확인
        verify(contentRepository).save(argThat(c -> 
            c.getTitle().equals("테스트 영화") &&
            c.getExternalId().equals("movie_123")
        ));
    }

    @Test
    @DisplayName("콘텐츠 저장 실패 - 이미 등록된 경우")
    void savedContent_Fail_Duplicate() {
        // given
        ContentRequestDto requestDto = ContentRequestDto.builder()
                .externalId("movie_1")
                .category(Category.MOVIE)
                .rating(0.0)
                .build();

        // 중복 체크 로직을 위해 mock 설정
        when(contentRepository.findByExternalIdAndCategory(any(), any()))
                .thenReturn(Optional.of(Content.builder().build()));

        // when & then
        assertThrows(IllegalStateException.class, () -> contentService.saveContent(requestDto));
    }

    @Test
    @DisplayName("경계값 테스트: 평점이 0 미만인 경우 실패")
    void saveContent_Fail_Rating_Low() {
        // given: 별점이 -1.0인 DTO
        ContentRequestDto requestDto = ContentRequestDto.builder()
                .rating(-1.0)
                .build();

        // when & then: 서비스 내부에 validateRating 로직이 남아있어야 테스트가 통과합니다.
        assertThrows(IllegalArgumentException.class, () -> contentService.saveContent(requestDto));
    }

    @Test
    @DisplayName("콘텐츠 저장 실패 - 평점 범위를 초과한 경우")
    void saveContent_Fail_Rating() {
        // given
        ContentRequestDto requestDto = ContentRequestDto.builder()
                .externalId("movie_1")
                .category(Category.MOVIE)
                .rating(10.0)
                .build();

        // when & then
        assertThrows(IllegalArgumentException.class, () -> {
            contentService.saveContent(requestDto);
        });
    }

    @Test
    @DisplayName("미래 날짜의 기록은 저장할 수 없다")
    void saveContent_Fail_FutureDate() {
        // given: 내일 날짜 설정
        ContentRequestDto requestDto = ContentRequestDto.builder()
            .rating(0.0)
            .viewDate(java.time.LocalDate.now().plusDays(1))
            .build();

        // when & then
        // (서비스 로직에 미래 날짜 체크가 있다면)
        assertThrows(IllegalArgumentException.class, () -> contentService.saveContent(requestDto));
    }
}