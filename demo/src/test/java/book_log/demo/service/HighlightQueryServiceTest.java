package book_log.demo.service;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.domain.Highlight;
import book_log.demo.dto.response.HighlightResponseDto;
import book_log.demo.repository.ContentRepository;
import book_log.demo.repository.HighlightRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HighlightQueryServiceTest {

    @Mock
    private HighlightRepository highlightRepository;

    @Mock
    private ContentRepository contentRepository;

    @InjectMocks
    private HighlightService highlightService;

    @Test
    @DisplayName("조회한 하이라이트와 부모 콘텐츠 정보를 DTO로 옮긴다")
    @SuppressWarnings("unchecked")
    void mapsToDto() {
        Content content = Content.builder().id(10L).title("귀멸의 칼날").category(Category.ANIME_TVA).build();
        Highlight highlight = Highlight.builder()
                .id(1L).text("마음을 불태워라").season(1).episode(8).timestamp("00:21:30")
                .content(content)
                .build();
        when(highlightRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(highlight));

        List<HighlightResponseDto> result = highlightService.getHighlights(2026, Category.ANIME);

        assertThat(result).hasSize(1);
        HighlightResponseDto dto = result.get(0);
        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.text()).isEqualTo("마음을 불태워라");
        assertThat(dto.page()).isNull();
        assertThat(dto.season()).isEqualTo(1);
        assertThat(dto.episode()).isEqualTo(8);
        assertThat(dto.timestamp()).isEqualTo("00:21:30");
        assertThat(dto.contentId()).isEqualTo(10L);
        assertThat(dto.contentTitle()).isEqualTo("귀멸의 칼날");
        assertThat(dto.contentCategory()).isEqualTo(Category.ANIME_TVA);
    }

    @Test
    @DisplayName("최신순 정렬(createdAt desc, id desc)로 조회를 요청한다")
    @SuppressWarnings("unchecked")
    void requestsLatestFirstSort() {
        when(highlightRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        highlightService.getHighlights(null, null);

        verify(highlightRepository).findAll(any(Specification.class),
                eq(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    @Test
    @DisplayName("결과가 없으면 빈 리스트를 반환한다")
    @SuppressWarnings("unchecked")
    void emptyResult() {
        when(highlightRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        assertThat(highlightService.getHighlights(null, null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1899, 2101, 0, -1})
    @DisplayName("범위(1900~2100) 밖 연도는 조회 전에 거절한다")
    @SuppressWarnings("unchecked")
    void rejectsInvalidYear(int year) {
        assertThatThrownBy(() -> highlightService.getHighlights(year, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(highlightRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("null/ALL은 필터 없음, ANIME는 애니 묶음, 나머지는 단일 카테고리")
    void resolveCategories() {
        assertThat(HighlightService.resolveCategories(null)).isNull();
        assertThat(HighlightService.resolveCategories(Category.ALL)).isNull();
        assertThat(HighlightService.resolveCategories(Category.ANIME))
                .containsExactlyInAnyOrder(Category.ANIME, Category.ANIME_MOVIE, Category.ANIME_TVA);
        assertThat(HighlightService.resolveCategories(Category.BOOK)).containsExactly(Category.BOOK);
        assertThat(HighlightService.resolveCategories(Category.ANIME_MOVIE)).containsExactly(Category.ANIME_MOVIE);
    }
}
