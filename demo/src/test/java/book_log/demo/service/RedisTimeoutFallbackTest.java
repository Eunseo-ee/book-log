package book_log.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import book_log.demo.config.ApiConfig;
import book_log.demo.config.CacheErrorConfig;
import book_log.demo.config.OperationsExposureConfig;
import book_log.demo.config.RedisCacheConfig;
import book_log.demo.domain.Category;
import book_log.demo.dto.response.SearchResponse;
import book_log.demo.dto.response.TmdbResponse;

/**
 * Redis 장애(25-g): 연결 거부 / 연결은 됐지만 응답이 없음, 두 경우 모두 timeout 안에 외부 API 경로로 정상 진행해야 한다.
 *
 * Redis 설정은 직접 만들지 않고 운영과 같은 경로로 들어온다:
 * Boot의 RedisAutoConfiguration(spring.data.redis.*) + 운영 설정 클래스(RedisCacheConfig, CacheErrorConfig, OperationsExposureConfig).
 * 그래서 ops-defaults.properties의 기본 timeout이 실제로 적용되는지도 함께 검증된다.
 * 실제 Redis는 쓰지 않는다. '응답 없는 Redis'는 연결만 accept하고 아무것도 보내지 않는 로컬 ServerSocket으로 재현한다.
 */
@Timeout(60) // 어떤 경우에도 테스트 자체가 영원히 매달리지 않게 하는 상한
class RedisTimeoutFallbackTest {

    private final List<Socket> accepted = new CopyOnWriteArrayList<>();
    private ServerSocket silentServer;

    @AfterEach
    void closeSockets() throws IOException {
        for (Socket s : accepted) {
            s.close();
        }
        if (silentServer != null) {
            silentServer.close();
        }
    }

    @Configuration
    static class ProviderConfig {
        @Bean
        RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        ApiConfig apiConfig() {
            // @ConfigurationProperties+@Validated 빈이라 mock이면 바인딩 검증에서 실패한다. 실제 객체를 쓰고 값은 위 api.* 속성이 채운다
            return new ApiConfig();
        }

        @Bean
        TmdbSearchService tmdbSearchService(RestTemplate restTemplate, ApiConfig apiConfig) {
            return new TmdbSearchService(restTemplate, apiConfig);
        }
    }

    private ApplicationContextRunner runner(int port, String... extraProps) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withUserConfiguration(OperationsExposureConfig.class, RedisCacheConfig.class,
                        CacheErrorConfig.class, ProviderConfig.class)
                .withPropertyValues("spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=" + port,
                        "spring.data.redis.ssl.enabled=false",
                        // ApiConfig(@ConfigurationProperties, @NotBlank)가 바인딩 대상이 되므로 가짜 값이 필요하다
                        "api.kakao-key=fake", "api.tmdb-token=fake")
                .withPropertyValues(extraProps);
    }

    private static void stubTmdb(RestTemplate restTemplate) {
        TmdbResponse.TmdbItem item = new TmdbResponse.TmdbItem();
        item.setMediaType("movie");
        item.setTitle("엘리멘탈");
        TmdbResponse body = new TmdbResponse();
        body.setResults(List.of(item));
        body.setTotalPages(1);
        body.setTotalResults(1);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(TmdbResponse.class)))
                .thenReturn(ResponseEntity.ok(body));
    }

    @Test
    @DisplayName("기본 설정: 명령/연결 timeout이 Lettuce 기본값(60초/10초)이 아니라 짧은 값으로 적용된다")
    void defaultTimeoutsAreShort() {
        runner(unusedPort()).run(ctx -> {
            LettuceConnectionFactory factory = ctx.getBean(LettuceConnectionFactory.class);
            assertThat(factory.getClientConfiguration().getCommandTimeout()).isEqualTo(Duration.ofSeconds(1));
            assertThat(factory.getClientConfiguration().getClientOptions().orElseThrow()
                    .getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
        });
    }

    @Test
    @DisplayName("환경변수(SPRING_DATA_REDIS_TIMEOUT / CONNECTTIMEOUT)로 기본값을 덮어쓸 수 있다")
    void timeoutsAreOverridable() {
        runner(unusedPort(), "spring.data.redis.timeout=250ms", "spring.data.redis.connect-timeout=300ms").run(ctx -> {
            LettuceConnectionFactory factory = ctx.getBean(LettuceConnectionFactory.class);
            assertThat(factory.getClientConfiguration().getCommandTimeout()).isEqualTo(Duration.ofMillis(250));
            assertThat(factory.getClientConfiguration().getClientOptions().orElseThrow()
                    .getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofMillis(300));
        });
    }

    @Test
    @DisplayName("a) Redis 연결 거부(다운): 예외 없이 외부 API 결과를 반환한다")
    void redisConnectionRefused_proceedsWithoutCache() {
        runner(unusedPort()).run(ctx -> {
            RestTemplate restTemplate = ctx.getBean(RestTemplate.class);
            stubTmdb(restTemplate);
            SearchProvider tmdb = ctx.getBean(SearchProvider.class);

            SearchResponse first = assertTimeoutPreemptively(Duration.ofSeconds(15),
                    () -> tmdb.search(Category.ALL, "엘리멘탈", 1));
            SearchResponse second = assertTimeoutPreemptively(Duration.ofSeconds(15),
                    () -> tmdb.search(Category.ALL, "엘리멘탈", 1));

            assertThat(first.getResults()).hasSize(1);
            assertThat(second.getResults()).hasSize(1);
            verify(restTemplate, times(2)).exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(TmdbResponse.class));
        });
    }

    @Test
    @DisplayName("b) Redis가 연결만 받고 응답이 없음: 기본 timeout 안에 끝나고 외부 API 결과를 반환한다")
    void redisHangs_finishesWithinTimeoutAndProceeds() throws IOException {
        int port = startSilentServer();

        runner(port).run(ctx -> {
            RestTemplate restTemplate = ctx.getBean(RestTemplate.class);
            stubTmdb(restTemplate);
            SearchProvider tmdb = ctx.getBean(SearchProvider.class);

            long start = System.nanoTime();
            // 기본 timeout 1초 기준: 조회(get)와 저장(put)에서 각각 최대 1초(+연결 시도) 대기. 60초 기본값이면 이 상한을 넘겨 실패한다.
            SearchResponse response = assertTimeoutPreemptively(Duration.ofSeconds(15),
                    () -> tmdb.search(Category.ALL, "엘리멘탈", 1));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(response.getResults()).hasSize(1);
            verify(restTemplate, times(1)).exchange(any(URI.class), eq(HttpMethod.GET), any(), eq(TmdbResponse.class));
            assertThat(elapsedMs).as("Redis 무응답 시 요청 소요 시간(ms)").isLessThan(8_000);
            assertThat(accepted).as("테스트가 의도한 '연결은 되지만 응답 없음' 상황이었는지").isNotEmpty();
        });
    }

    /** 연결을 accept하고 아무것도 읽지도 쓰지도 않는 서버 (연결을 닫지 않는다). */
    private int startSilentServer() throws IOException {
        silentServer = new ServerSocket(0);
        Thread acceptor = new Thread(() -> {
            while (!silentServer.isClosed()) {
                try {
                    accepted.add(silentServer.accept());
                } catch (IOException e) {
                    return; // 서버가 닫히면 종료
                }
            }
        }, "silent-redis-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        return silentServer.getLocalPort();
    }

    private static int unusedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort(); // 바로 닫으므로 아무도 listen하지 않는 포트
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
