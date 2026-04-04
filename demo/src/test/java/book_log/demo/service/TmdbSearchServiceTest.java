package book_log.demo.service;

import book_log.demo.config.ApiConfig;
import book_log.demo.domain.Category;
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
    private TmdbSearchService tmdbSearchService; // 가짜들을 주입받은 테스트

    @Test
    @DisplayName("애니메이션 장르 ID(16)가 포함되면 ANIME_MOVIE 카테고리로 분류되어야 한다")
    void mapToAnimeMovieTest() {
        String query = "Re:제로";
        when(apiConfig.getTmdbToken()).thenReturn("test-token");

        // 가짜 응답 데이터 생성
        TmdbResponse.TmdbItem mockItem = new TmdbResponse.TmdbItem();
        mockItem.setMediaType("movie");
        mockItem.setGenreIds(List.of(16, 28));
        mockItem.setTitle("Re:제로부터 시작하는 이세계 생활");

        TmdbResponse mockResponse = new TmdbResponse();
        mockResponse.setResults(List.of(mockItem));

        // restTemplate이 호출되면 위에서 만든 가짜 응답을 돌려주라고 설정
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        // when (실행)
        List<UnifiedSearchResponse> results = tmdbSearchService.search(Category.ALL, query);

        // then (검증)
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getCategory()).isEqualTo(Category.ANIME_MOVIE);
        assertThat(results.get(0).getTitle()).isEqualTo("Re:제로부터 시작하는 이세계 생활");
    }

    @Test
    @DisplayName("검색 결과가 비어있으면 빈 리스트를 반환해야 한다")
    void emptyResultTest() {
        // given
        when(apiConfig.getTmdbToken()).thenReturn("test-token");
        
        TmdbResponse emptyResponse = new TmdbResponse();
        emptyResponse.setResults(List.of()); // 빈 리스트 설정

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(emptyResponse));

        // when
        List<UnifiedSearchResponse> results = tmdbSearchService.search(Category.ALL, "존재하지않는영화제목");

        // then
        assertThat(results).isEmpty(); // 결과가 비어있는지 검증
    }

    @Test
    @DisplayName("애니메이션이 아닌 일반 영화는 MOVIE 카테고리로 분류되어야 한다")
    void mapToMovieTest() {
        // given
        TmdbResponse.TmdbItem mockItem = new TmdbResponse.TmdbItem();
        mockItem.setMediaType("movie");
        mockItem.setGenreIds(List.of(28, 12)); // 16번(애니)이 없음
        mockItem.setTitle("범죄도시");

        TmdbResponse mockResponse = new TmdbResponse();
        mockResponse.setResults(List.of(mockItem));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        // when
        List<UnifiedSearchResponse> results = tmdbSearchService.search(Category.ALL, "범죄도시");

        // then
        assertThat(results.get(0).getCategory()).isEqualTo(Category.MOVIE);
    }

    @Test
    @DisplayName("장르 ID가 null이어도 에러가 발생하지 않고 일반 카테고리로 분류되어야 한다")
    void nullGenreIdsTest() {
        // given
        TmdbResponse.TmdbItem mockItem = new TmdbResponse.TmdbItem();
        mockItem.setMediaType("movie");
        mockItem.setGenreIds(null); // 장르 정보가 아예 없는 경우
        mockItem.setTitle("제목만 있는 영화");

        TmdbResponse mockResponse = new TmdbResponse();
        mockResponse.setResults(List.of(mockItem));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        // when & then (에러 없이 실행되는지 확인)
        List<UnifiedSearchResponse> results = tmdbSearchService.search(Category.ALL, "test");
        assertThat(results.get(0).getCategory()).isEqualTo(Category.MOVIE);
    }

    @Test
    @DisplayName("API 호출 시 타임아웃(ResourceAccessException)이 발생하면 빈 리스트를 반환해야 한다")
    void timeoutHandlingTest() {
        // given: restTemplate 호출 시 강제로 타임아웃 예외(ResourceAccessException)를 던지도록 설정
        when(apiConfig.getTmdbToken()).thenReturn("test-token");
        
        // 실제 네트워크 지연 대신 Mockito가 예외를 던지게 만듭니다.
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Timeout occurred"));

        // when: 서비스 호출
        List<UnifiedSearchResponse> results = tmdbSearchService.search(Category.ALL, "타임아웃테스트");

        // then: 에러가 터지지 않고 빈 리스트가 돌아오는지 확인 (우리가 짠 try-catch 덕분!)
        assertThat(results).isEmpty();
        // 로그가 남았는지 확인하는 코드는 복잡하니, 결과값이 빈 리스트인 것만 확인해도 충분합니다!
    }

    @Test
    @DisplayName("API 서버가 500 에러를 던져도 서버가 터지지 않고 빈 리스트를 반환해야 한다")
    void apiServerErrorHandlingTest() {
        // given: 500 에러 상황 모사
        when(apiConfig.getTmdbToken()).thenReturn("test-token");
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenThrow(new org.springframework.web.client.HttpServerErrorException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        // when
        List<UnifiedSearchResponse> results = tmdbSearchService.search(Category.ALL, "서버에러테스트");

        // then
        assertThat(results).isEmpty();
    }
}
