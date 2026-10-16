package book_log.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import book_log.demo.domain.Category;
import book_log.demo.dto.response.UnifiedSearchResponse;
import book_log.demo.service.ContentService.SavedKey;

/**
 * SearchService가 Provider(캐시 대상) 결과에 isSaved를 '캐시 바깥에서' 채우는지 검증.
 */
class SearchServiceTest {

    private SearchProvider provider;
    private ContentService contentService;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        provider = mock(SearchProvider.class);
        contentService = mock(ContentService.class);
        searchService = new SearchService(List.of(provider), contentService);

        when(provider.supports(any())).thenReturn(true);
    }

    @Test
    @DisplayName("DB에 저장된 (externalId, category) 쌍만 isSaved=true가 된다")
    void marksSavedOnlyForMatchingIdAndCategory() {
        UnifiedSearchResponse saved = result("550", "파이트 클럽", Category.MOVIE);
        UnifiedSearchResponse sameIdOtherCategory = result("550", "같은 번호의 드라마", Category.TV);
        UnifiedSearchResponse notSaved = result("999", "저장 안 된 영화", Category.MOVIE);

        when(provider.search(any(), anyString())).thenReturn(List.of(saved, sameIdOtherCategory, notSaved));
        when(contentService.findSavedKeys(anyCollection()))
                .thenReturn(Set.of(new SavedKey("550", Category.MOVIE)));

        List<UnifiedSearchResponse> results = searchService.search(Category.MOVIE, "query");

        assertThat(results).extracting(UnifiedSearchResponse::getIsSaved)
                .containsExactly(true, false, false);
    }

    @Test
    @DisplayName("원본(캐시에서 꺼낸) 객체는 수정하지 않고 복사본에만 isSaved를 채운다")
    void doesNotMutateOriginalObjects() {
        UnifiedSearchResponse original = result("550", "파이트 클럽", Category.MOVIE);

        when(provider.search(any(), anyString())).thenReturn(List.of(original));
        when(contentService.findSavedKeys(anyCollection()))
                .thenReturn(Set.of(new SavedKey("550", Category.MOVIE)));

        List<UnifiedSearchResponse> results = searchService.search(Category.MOVIE, "query");

        assertThat(results.get(0).getIsSaved()).isTrue();
        assertThat(original.getIsSaved()).isNull(); // 캐시 항목이 오염되지 않았다
        assertThat(results.get(0)).isNotSameAs(original);
        assertThat(results.get(0).getTitle()).isEqualTo("파이트 클럽"); // 나머지 필드는 그대로 복사
    }

    @Test
    @DisplayName("같은 검색 결과를 두 번 받아도, 그 사이 저장 상태가 바뀌면 isSaved가 최신 값으로 나온다")
    void reflectsSavedStateChangeBetweenCalls() {
        UnifiedSearchResponse cachedItem = result("550", "파이트 클럽", Category.MOVIE);

        when(provider.search(any(), anyString())).thenReturn(List.of(cachedItem));
        when(contentService.findSavedKeys(anyCollection()))
                .thenReturn(Set.of())
                .thenReturn(Set.of(new SavedKey("550", Category.MOVIE)));

        boolean before = searchService.search(Category.MOVIE, "query").get(0).getIsSaved();
        boolean after = searchService.search(Category.MOVIE, "query").get(0).getIsSaved();

        assertThat(before).isFalse();
        assertThat(after).isTrue();
    }

    @Test
    @DisplayName("저장 여부는 건별 조회가 아니라 한 번의 배치 조회로 확인하고, null/중복 externalId는 제외한다")
    void queriesOnceWithDistinctNonNullIds() {
        when(provider.search(any(), anyString())).thenReturn(List.of(
                result("550", "A", Category.MOVIE),
                result("550", "B", Category.TV),
                result(null, "ID 없음", Category.BOOK)));
        when(contentService.findSavedKeys(anyCollection())).thenReturn(Set.of());

        List<UnifiedSearchResponse> results = searchService.search(Category.ALL, "query");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(contentService, times(1)).findSavedKeys(captor.capture());
        assertThat(captor.getValue()).containsExactly("550");
        // externalId가 없는 결과도 응답에서 사라지지 않고 isSaved=false로 내려간다
        assertThat(results).hasSize(3);
        assertThat(results.get(2).getIsSaved()).isFalse();
    }

    @Test
    @DisplayName("검색 결과가 없으면 DB를 조회하지 않는다")
    void emptyResults_skipsDbLookup() {
        when(provider.search(any(), anyString())).thenReturn(List.of());

        List<UnifiedSearchResponse> results = searchService.search(Category.MOVIE, "query");

        assertThat(results).isEmpty();
        verify(contentService, never()).findSavedKeys(any());
    }

    private UnifiedSearchResponse result(String externalId, String title, Category category) {
        return UnifiedSearchResponse.builder()
                .externalId(externalId)
                .title(title)
                .category(category)
                .build();
    }
}
