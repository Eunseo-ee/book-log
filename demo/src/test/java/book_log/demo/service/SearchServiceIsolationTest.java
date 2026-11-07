package book_log.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;

import book_log.demo.domain.Category;
import book_log.demo.dto.response.SearchResponse;
import book_log.demo.dto.response.UnifiedSearchResponse;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 통합 검색(ALL)에서 Provider 하나가 실패해도 나머지 결과가 정상 반환되는지 확인한다.
 * 실패 Provider는 로그에 남는다. 응답 구조(SearchResponse)는 그대로다.
 */
class SearchServiceIsolationTest {

    private SearchProvider books;
    private SearchProvider movies;
    private ContentService contentService;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        books = mock(SearchProvider.class);
        movies = mock(SearchProvider.class);
        contentService = mock(ContentService.class);
        when(contentService.findSavedKeys(eq(1L), any())).thenReturn(Set.of());

        logger = (Logger) LoggerFactory.getLogger(SearchService.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private SearchService service() {
        return new SearchService(List.of(books, movies), contentService);
    }

    private static SearchResponse page(String title, Category category, double vote, long total, int pages) {
        return SearchResponse.builder()
                .results(List.of(UnifiedSearchResponse.builder().externalId(title).title(title)
                        .category(category).voteAverage(vote).build()))
                .totalResults(total)
                .totalPages(pages)
                .build();
    }

    @Test
    @DisplayName("한 Provider가 예외를 던져도 나머지 결과와 페이지 정보가 정상 반환된다")
    void oneProviderThrows_othersReturned() {
        when(books.search(any(), anyString(), anyInt())).thenThrow(new ResourceAccessException("Read timed out"));
        when(movies.search(any(), anyString(), anyInt())).thenReturn(page("영화", Category.MOVIE, 8.0, 7, 4));

        SearchResponse response = service().search(1L, Category.ALL, "q", 1);

        assertThat(response.getResults()).extracting(UnifiedSearchResponse::getTitle).containsExactly("영화");
        assertThat(response.getTotalResults()).isEqualTo(7);
        assertThat(response.getTotalPages()).isEqualTo(4);
    }

    @Test
    @DisplayName("뒤 순서 Provider가 실패해도 앞 Provider 결과는 정상 반환된다")
    void failureOrderDoesNotMatter() {
        when(books.search(any(), anyString(), anyInt())).thenReturn(page("책", Category.BOOK, 0, 3, 1));
        when(movies.search(any(), anyString(), anyInt())).thenThrow(new IllegalStateException("boom"));

        SearchResponse response = service().search(1L, Category.ALL, "q", 1);

        assertThat(response.getResults()).extracting(UnifiedSearchResponse::getTitle).containsExactly("책");
    }

    @Test
    @DisplayName("실패한 Provider는 WARN 로그로 남고 원인 예외가 보존된다")
    void failureIsLogged() {
        when(books.search(any(), anyString(), anyInt())).thenThrow(new ResourceAccessException("Read timed out"));
        when(movies.search(any(), anyString(), anyInt())).thenReturn(page("영화", Category.MOVIE, 8.0, 1, 1));

        service().search(1L, Category.ALL, "q", 1);

        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo("WARN");
        assertThat(event.getFormattedMessage()).contains("검색 Provider 실패");
        assertThat(event.getThrowableProxy().getMessage()).contains("Read timed out");
    }

    @Test
    @DisplayName("Provider가 null을 반환해도 나머지 결과는 정상 반환된다")
    void providerReturnsNull() {
        when(books.search(any(), anyString(), anyInt())).thenReturn(null);
        when(movies.search(any(), anyString(), anyInt())).thenReturn(page("영화", Category.MOVIE, 8.0, 1, 1));

        SearchResponse response = service().search(1L, Category.ALL, "q", 1);

        assertThat(response.getResults()).hasSize(1);
    }

    @Test
    @DisplayName("전부 실패하면 예외 없이 빈 결과(totalPages 1, totalResults 0)를 반환한다 - Provider 단독 실패 응답과 같은 형태")
    void allProvidersFail_emptyResponse() {
        when(books.search(any(), anyString(), anyInt())).thenThrow(new ResourceAccessException("down"));
        when(movies.search(any(), anyString(), anyInt())).thenThrow(new RuntimeException("down"));

        SearchResponse response = service().search(1L, Category.ALL, "q", 1);

        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.getTotalResults()).isZero();
        assertThat(logs.list).hasSize(2);
    }

    @Test
    @DisplayName("모든 Provider가 정상이면 기존처럼 합쳐서 평점순으로 정렬한다 (회귀 방지)")
    void allSucceed_mergedAndSorted() {
        when(books.search(any(), anyString(), anyInt())).thenReturn(page("책", Category.BOOK, 0, 3, 2));
        when(movies.search(any(), anyString(), anyInt())).thenReturn(page("영화", Category.MOVIE, 8.0, 7, 4));

        SearchResponse response = service().search(1L, Category.ALL, "q", 1);

        assertThat(response.getResults()).extracting(UnifiedSearchResponse::getTitle).containsExactly("영화", "책");
        assertThat(response.getTotalResults()).isEqualTo(10);
        assertThat(response.getTotalPages()).isEqualTo(4);
        assertThat(logs.list).isEmpty();
    }
}
