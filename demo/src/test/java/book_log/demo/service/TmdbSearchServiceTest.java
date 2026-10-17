package book_log.demo.service;

import book_log.demo.config.ApiConfig;
import book_log.demo.domain.Category;
import book_log.demo.dto.response.SearchResponse;
import book_log.demo.dto.response.TmdbResponse;
import book_log.demo.dto.response.UnifiedSearchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat; // 핵심 검증 라이브러리
import static org.mockito.ArgumentMatchers.*;            // anyString() 등 매개변수 처리
import static org.mockito.Mockito.when;                  // 가짜 객체 동작 설정

@ExtendWith(MockitoExtension.class)
public class TmdbSearchServiceTest {
    
    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ApiConfig apiConfig;

    @InjectMocks
    private TmdbSearchService tmdbSearchService;

    @Test
    @DisplayName("애니메이션 장르 ID(16)가 포함되면 ANIME_MOVIE 카테고리로 분류되어야 한다")
    void mapToAnimeMovieTest() {
        // given
        String query = "Re:제로";
        int page = 1;
        when(apiConfig.getTmdbToken()).thenReturn("test-token");

        TmdbResponse.TmdbItem mockItem = new TmdbResponse.TmdbItem();
        mockItem.setMediaType("movie");
        mockItem.setGenreIds(List.of(16, 28));
        mockItem.setTitle("Re:제로부터 시작하는 이세계 생활");

        TmdbResponse mockResponse = new TmdbResponse();
        mockResponse.setResults(List.of(mockItem));
        mockResponse.setTotalPages(1);    // 페이지 정보 추가
        mockResponse.setTotalResults(1);  // 결과 수 정보 추가

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        // when (반환 타입 변경: List -> SearchResponse)
        SearchResponse response = tmdbSearchService.search(Category.ALL, query, page);

        // then (결과 리스트를 꺼내서 검증)
        assertThat(response.getResults()).isNotEmpty();
        assertThat(response.getResults().get(0).getCategory()).isEqualTo(Category.ANIME_MOVIE);
        assertThat(response.getTotalResults()).isEqualTo(1);
    }

    @Test
    @DisplayName("검색 결과가 비어있으면 빈 결과 객체를 반환해야 한다")
    void emptyResultTest() {
        // given
        when(apiConfig.getTmdbToken()).thenReturn("test-token");
        TmdbResponse emptyResponse = new TmdbResponse();
        emptyResponse.setResults(List.of());
        emptyResponse.setTotalPages(1);
        emptyResponse.setTotalResults(0);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(emptyResponse));

        // when
        SearchResponse response = tmdbSearchService.search(Category.ALL, "존재하지않는영화", 1);

        // then
        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalResults()).isEqualTo(0);
    }

    @Test
    @DisplayName("API 호출 시 타임아웃 발생 시 빈 결과 객체를 반환해야 한다")
    void timeoutHandlingTest() {
        // given
        when(apiConfig.getTmdbToken()).thenReturn("test-token");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Timeout"));

        // when
        SearchResponse response = tmdbSearchService.search(Category.ALL, "timeout", 1);

        // then (catch 블록에서 생성한 기본 빈 객체 검증)
        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("애니메이션이 아닌 일반 영화는 MOVIE 카테고리로 분류되어야 한다")
    void mapToMovieTest() {
        // given
        when(apiConfig.getTmdbToken()).thenReturn("test-token");
        TmdbResponse.TmdbItem mockItem = new TmdbResponse.TmdbItem();
        mockItem.setMediaType("movie");
        mockItem.setGenreIds(List.of(28, 12));
        mockItem.setTitle("범죄도시");

        TmdbResponse mockResponse = new TmdbResponse();
        mockResponse.setResults(List.of(mockItem));
        mockResponse.setTotalPages(1);
        mockResponse.setTotalResults(1);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        // when
        SearchResponse response = tmdbSearchService.search(Category.ALL, "범죄도시", 1);

        // then
        assertThat(response.getResults().get(0).getCategory()).isEqualTo(Category.MOVIE);
    }

    @Test
    @DisplayName("API 응답 중 일부 항목의 제목이 null이어도 필터링 후 나머지는 반환해야 한다")
    void nullTitleItemTest() {
        // given
        when(apiConfig.getTmdbToken()).thenReturn("test-token");
        
        TmdbResponse.TmdbItem badItem = new TmdbResponse.TmdbItem();
        badItem.setTitle(null);
        badItem.setMediaType("movie");

        TmdbResponse.TmdbItem goodItem = new TmdbResponse.TmdbItem();
        goodItem.setTitle("정상 영화");
        goodItem.setMediaType("movie");

        TmdbResponse mockResponse = new TmdbResponse();
        mockResponse.setResults(List.of(badItem, goodItem));
        mockResponse.setTotalPages(1);

        when(restTemplate.exchange(anyString(), any(), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        // when
        SearchResponse response = tmdbSearchService.search(Category.ALL, "test", 1);

        // then
        assertThat(response.getResults()).isNotEmpty();
        // 서비스의 mapToResponse 로직에 따라 결과 개수 확인 가능
        // 만약 null 필터가 있다면 assertThat(response.getResults()).hasSize(1);
    }
    
    @Test
    @DisplayName("TMDB id가 externalId(문자열)로 매핑되고, isSaved는 Provider에서 채우지 않는다")
    void externalIdMappedFromTmdbId() {
        // given
        when(apiConfig.getTmdbToken()).thenReturn("test-token");

        TmdbResponse.TmdbItem mockItem = new TmdbResponse.TmdbItem();
        mockItem.setId(550L);
        mockItem.setMediaType("movie");
        mockItem.setTitle("파이트 클럽");

        TmdbResponse mockResponse = new TmdbResponse();
        mockResponse.setResults(List.of(mockItem));
        mockResponse.setTotalPages(1);
        mockResponse.setTotalResults(1);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        // when
        SearchResponse response = tmdbSearchService.search(Category.ALL, "파이트 클럽", 1);

        // then
        assertThat(response.getResults().get(0).getExternalId()).isEqualTo("550");
        assertThat(response.getResults().get(0).getIsSaved()).isNull();
    }

    @Test
    @DisplayName("TMDB id가 null이면 externalId는 문자열 \"null\"이 아니라 null이어야 한다")
    void nullTmdbId_givesNullExternalId() {
        // given
        when(apiConfig.getTmdbToken()).thenReturn("test-token");

        TmdbResponse.TmdbItem mockItem = new TmdbResponse.TmdbItem();
        mockItem.setMediaType("movie");
        mockItem.setTitle("id 없는 영화");

        TmdbResponse mockResponse = new TmdbResponse();
        mockResponse.setResults(List.of(mockItem));
        mockResponse.setTotalPages(1);
        mockResponse.setTotalResults(1);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        // when
        SearchResponse response = tmdbSearchService.search(Category.ALL, "test", 1);

        // then
        assertThat(response.getResults().get(0).getExternalId()).isNull();
    }
}