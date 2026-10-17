package book_log.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestTemplate;

import book_log.demo.config.ApiConfig;
import book_log.demo.config.CacheErrorConfig;
import book_log.demo.config.RedisCacheConfig;
import book_log.demo.domain.Category;
import book_log.demo.dto.response.SearchResponse;
import book_log.demo.dto.response.TmdbResponse;

/**
 * Redis가 죽어 있어도 검색은 외부 API로 대체 호출되어 정상 동작해야 한다.
 * 실제 운영 설정(RedisCacheConfig + CacheErrorConfig)을 그대로 가져오되,
 * Redis 연결만 아무도 listen하지 않는 포트로 돌려서 '장애 상황'을 재현한다. (Redis 서버 불필요)
 */
@SpringJUnitConfig(CacheFailureFallbackTest.TestConfig.class)
class CacheFailureFallbackTest {

    @Configuration
    @Import({ RedisCacheConfig.class, CacheErrorConfig.class })
    static class TestConfig {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory("localhost", unusedPort());
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

        private static int unusedPort() {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort(); // 바로 닫으므로 이 포트는 아무도 사용하지 않음
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Autowired
    private SearchProvider tmdbSearchService;

    @Autowired
    private RestTemplate restTemplate;

    @Test
    @DisplayName("Redis 연결이 불가능해도 예외 없이 외부 API 결과를 반환한다")
    void redisDown_fallsBackToExternalApi() {
        TmdbResponse.TmdbItem item = new TmdbResponse.TmdbItem();
        item.setMediaType("movie");
        item.setTitle("엘리멘탈");
        TmdbResponse response = new TmdbResponse();
        response.setResults(List.of(item));
        response.setTotalPages(1);
        response.setTotalResults(1);


        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        SearchResponse first = tmdbSearchService.search(Category.ALL, "엘리멘탈", 1);
        SearchResponse second = tmdbSearchService.search(Category.ALL, "엘리멘탈", 1);

        assertThat(first.getResults()).hasSize(1);
        assertThat(second.getResults()).hasSize(1);
        // 캐시를 쓸 수 없으니 매번 외부 API를 호출 (성능은 떨어지지만 기능은 유지)
        verify(restTemplate, times(2))
                .exchange(anyString(), eq(HttpMethod.GET), any(), eq(TmdbResponse.class));
    }
}