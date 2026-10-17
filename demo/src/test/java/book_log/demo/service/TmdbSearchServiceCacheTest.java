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
import book_log.demo.dto.response.SearchResponse;
import book_log.demo.dto.response.TmdbResponse;

/**
 * TmdbSearchService의 캐시 규칙 검증.
 * 캐시는 프록시(AOP)로 동작하므로 Mockito 단독 테스트로는 확인할 수 없고,
 * 최소한의 스프링 컨텍스트(@EnableCaching + 메모리 캐시)를 띄워서 검증한다. Redis/DB는 필요 없다.
 *
 * 캐시 키는 "검색어 + 카테고리 + 페이지"다.
 * 결과가 카테고리별로 필터링되고(mapToResponse) 페이지마다 다르므로, 셋 중 하나라도 다르면 다른 캐시 항목이어야 한다.
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
    @DisplayName("같은 검색어·카테고리·페이지를 두 번 검색하면 TMDB는 한 번만 호출된다")
    void sameQueryCategoryAndPage_callsTmdbOnce() {
        stubTmdbReturns(movieResponse("엘리멘탈"));

        tmdbSearchService.search(Category.MOVIE, "엘리멘탈", 1);
        SearchResponse second = tmdbSearchService.search(Category.MOVIE, "엘리멘탈", 1);

        assertThat(second.getResults()).hasSize(1);
        verifyTmdbCalls(1);
    }

    @Test
    @DisplayName("페이지가 다르면 각각 TMDB를 호출한다 (1페이지 캐시를 2페이지에 돌려주면 안 된다)")
    void differentPage_callsAgain() {
        stubTmdbReturns(movieResponse("엘리멘탈"));

        tmdbSearchService.search(Category.MOVIE, "엘리멘탈", 1);
        tmdbSearchService.search(Category.MOVIE, "엘리멘탈", 2);

        verifyTmdbCalls(2);
    }

    @Test
    @DisplayName("카테고리가 다르면 캐시를 공유하지 않는다 (결과가 카테고리별로 필터링되기 때문)")
    void differentCategory_callsAgain() {
        stubTmdbReturns(movieResponse("엘리멘탈"));

        tmdbSearchService.search(Category.ALL, "엘리멘탈", 1);
        tmdbSearchService.search(Category.MOVIE, "엘리멘탈", 1);

        verifyTmdbCalls(2);
    }

    @Test
    @DisplayName("검색어가 다르면 각각 TMDB를 호출한다")
    void differentQuery_callsAgain() {
        stubTmdbReturns(movieResponse("결과"));

        tmdbSearchService.search(Category.ALL, "엘리멘탈", 1);
        tmdbSearchService.search(Category.ALL, "인사이드 아웃", 1);

        verifyTmdbCalls(2);
    }

    @Test
    @DisplayName("TMDB 장애로 반환된 빈 결과는 캐싱되지 않고, 복구되면 정상 결과가 캐싱된다")
    void apiFailure_isNotCached() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(ResponseEntity.ok(movieResponse("복구됨")));

        SearchResponse duringOutage = tmdbSearchService.search(Category.ALL, "엘리멘탈", 1);
        SearchResponse afterRecovery = tmdbSearchService.search(Category.ALL, "엘리멘탈", 1);
        SearchResponse cachedHit = tmdbSearchService.search(Category.ALL, "엘리멘탈", 1);

        assertThat(duringOutage.getResults()).isEmpty();
        assertThat(afterRecovery.getResults()).hasSize(1);
        assertThat(afterRecovery.getResults().get(0).getTitle()).isEqualTo("복구됨");
        assertThat(cachedHit.getResults()).hasSize(1);
        // 장애 1회 + 복구 후 1회 = 2회. 세 번째는 캐시 히트여야 한다.
        verifyTmdbCalls(2);
    }

    @Test
    @DisplayName("정상 응답이어도 결과가 0건이면 캐싱하지 않는다 (장애와 구분하지 않는 단순화의 트레이드오프)")
    void emptyResult_isNotCached() {
        TmdbResponse empty = new TmdbResponse();
        empty.setResults(List.of());
        empty.setTotalPages(1);
        empty.setTotalResults(0);
        stubTmdbReturns(empty);

        tmdbSearchService.search(Category.ALL, "존재하지않는제목", 1);
        tmdbSearchService.search(Category.ALL, "존재하지않는제목", 1);

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
        response.setTotalPages(1); 
        response.setTotalResults(1);
        return response;
    }
}