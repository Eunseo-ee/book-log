package book_log.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import book_log.demo.config.ApiConfig;
import book_log.demo.domain.Category;
import book_log.demo.dto.response.TmdbResponse;
import book_log.demo.dto.response.UnifiedSearchResponse;

/**
 * TmdbSearchService의 캐시 규칙 검증.
 * 캐시는 프록시(AOP)로 동작하므로 Mockito 단독 테스트로는 확인할 수 없고,
 * 최소한의 스프링 컨텍스트(@EnableCaching + 메모리 캐시)를 띄워서 검증한다. Redis/DB는 필요 없다.
 */
@SpringJUnitConfig(TmdbSearchServiceCacheTest.TestConfig.class)
class TmdbSearchServiceCacheTest {

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("tmdbSearch");
        }

        @Bean
        RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        ApiConfig apiConfig() {
            ApiConfig config = mock(ApiConfig.class);
            when(config.getTmdbToken()).thenReturn("test-token");
            return config;
        }

        @Bean
        TmdbSearchService tmdbSearchService(RestTemplate restTemplate, ApiConfig apiConfig) {
            return new TmdbSearchService(restTemplate, apiConfig);
        }
    }

    @Autowired
    private SearchProvider tmdbSearchService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("tmdbSearch").clear();
        reset(restTemplate);
    }

    @Test
    @DisplayName("같은 검색어를 두 번 검색하면 TMDB는 한 번만 호출된다")
    void sameQuery_callsTmdbOnce() {
        stubTmdbReturns(movieResponse("엘리멘탈"));

        tmdbSearchService.search(Category.MOVIE, "엘리멘탈");
        List<UnifiedSearchResponse> second = tmdbSearchService.search(Category.MOVIE, "엘리멘탈");

        assertThat(second).hasSize(1);
        verifyTmdbCalls(1);
    }

    @Test
    @DisplayName("카테고리가 달라도 검색어가 같으면 캐시를 공유한다 (ALL/MOVIE/ANIME)")
    void differentCategories_shareCacheEntry() {
        stubTmdbReturns(movieResponse("엘리멘탈"));

        tmdbSearchService.search(Category.ALL, "엘리멘탈");
        tmdbSearchService.search(Category.MOVIE, "엘리멘탈");
        tmdbSearchService.search(Category.ANIME, "엘리멘탈");

        verifyTmdbCalls(1);
    }

    @Test
    @DisplayName("검색어가 다르면 각각 TMDB를 호출한다")
    void differentQuery_callsAgain() {
        stubTmdbReturns(movieResponse("결과"));

        tmdbSearchService.search(Category.ALL, "엘리멘탈");
        tmdbSearchService.search(Category.ALL, "인사이드 아웃");

        verifyTmdbCalls(2);
    }

    @Test
    @DisplayName("TMDB 장애로 반환된 빈 리스트는 캐싱되지 않고, 복구되면 정상 결과가 캐싱된다")
    void apiFailure_isNotCached() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(ResponseEntity.ok(movieResponse("복구됨")));

        List<UnifiedSearchResponse> duringOutage = tmdbSearchService.search(Category.ALL, "엘리멘탈");
        List<UnifiedSearchResponse> afterRecovery = tmdbSearchService.search(Category.ALL, "엘리멘탈");
        List<UnifiedSearchResponse> cachedHit = tmdbSearchService.search(Category.ALL, "엘리멘탈");

        assertThat(duringOutage).isEmpty();
        assertThat(afterRecovery).hasSize(1);
        assertThat(afterRecovery.get(0).getTitle()).isEqualTo("복구됨");
        assertThat(cachedHit).hasSize(1);
        // 장애 1회 + 복구 후 1회 = 2회. 세 번째는 캐시 히트여야 한다.
        verifyTmdbCalls(2);
    }

    @Test
    @DisplayName("정상 응답이어도 결과가 0건이면 캐싱하지 않는다 (장애와 구분하지 않는 단순화의 트레이드오프)")
    void emptyResult_isNotCached() {
        TmdbResponse empty = new TmdbResponse();
        empty.setResults(List.of());
        stubTmdbReturns(empty);

        tmdbSearchService.search(Category.ALL, "존재하지않는제목");
        tmdbSearchService.search(Category.ALL, "존재하지않는제목");

        verifyTmdbCalls(2);
    }

    // ---- helpers ----

    private void stubTmdbReturns(TmdbResponse response) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
    }

    private void verifyTmdbCalls(int times) {
        verify(restTemplate, times(times))
                .exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class));
    }

    private TmdbResponse movieResponse(String title) {
        TmdbResponse.TmdbItem item = new TmdbResponse.TmdbItem();
        item.setMediaType("movie");
        item.setTitle(title);

        TmdbResponse response = new TmdbResponse();
        response.setResults(List.of(item));
        return response;
    }
}
