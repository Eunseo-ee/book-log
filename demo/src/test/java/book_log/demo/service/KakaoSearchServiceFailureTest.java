package book_log.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestTemplate;

import book_log.demo.config.ApiConfig;
import book_log.demo.domain.Category;
import book_log.demo.dto.response.SearchResponse;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;

/**
 * Kakao API가 실패해도 TMDB와 같은 정책(빈 응답, totalPages 1)으로 처리되고,
 * 로그에 API 키가 남지 않는지 확인한다. 진짜 RestTemplate + MockRestServiceServer (네트워크 없음).
 */
class KakaoSearchServiceFailureTest {

    private static final String KEY = "SECRET-KAKAO-KEY-12345";

    private MockRestServiceServer server;
    private KakaoSearchService kakao;
    private ListAppender<ILoggingEvent> logs;
    private Logger kakaoLogger;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        ApiConfig apiConfig = mock(ApiConfig.class);
        when(apiConfig.getKakaoKey()).thenReturn(KEY);
        kakao = new KakaoSearchService(restTemplate, apiConfig);

        kakaoLogger = (Logger) LoggerFactory.getLogger(KakaoSearchService.class);
        logs = new ListAppender<>();
        logs.start();
        kakaoLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        kakaoLogger.detachAppender(logs);
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of("401 인증 실패", withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"errorType\":\"AccessDeniedError\"}")),
                Arguments.of("400 잘못된 요청", withStatus(HttpStatus.BAD_REQUEST)),
                Arguments.of("429 호출 한도 초과", withStatus(HttpStatus.TOO_MANY_REQUESTS)),
                Arguments.of("500 서버 오류", withStatus(HttpStatus.INTERNAL_SERVER_ERROR)),
                Arguments.of("503 서비스 불가", withStatus(HttpStatus.SERVICE_UNAVAILABLE)),
                Arguments.of("읽기 타임아웃", withException(new SocketTimeoutException("Read timed out"))),
                Arguments.of("연결 실패", withException(new IOException("Connection refused"))),
                Arguments.of("JSON이 아닌 응답", withSuccess("<html>gateway error</html>", MediaType.APPLICATION_JSON)),
                Arguments.of("필드 타입이 틀린 JSON", withSuccess("{\"documents\":\"oops\",\"meta\":{}}", MediaType.APPLICATION_JSON)),
                Arguments.of("빈 본문(200)", withSuccess()),
                Arguments.of("meta 없는 응답", withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures")
    @DisplayName("Kakao 실패는 예외 없이 규격에 맞는 빈 응답이 되고, 로그에 API 키가 남지 않는다")
    void failureBecomesEmptyResponse(String ignoredName, ResponseCreator failure) {
        server.expect(request -> { }).andRespond(failure);

        SearchResponse response = kakao.search(Category.BOOK, "테스트", 1);

        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.getTotalResults()).isZero();

        assertThat(logs.list).as("실패는 ERROR 로그로 남는다").anyMatch(e -> e.getLevel().toString().equals("ERROR"));
        assertThat(allLogText()).doesNotContain(KEY).doesNotContain("KakaoAK");
    }

    @Test
    @DisplayName("정상 응답은 기존처럼 매핑된다 (실패 처리가 정상 경로를 가리지 않는다)")
    void successStillWorks() {
        server.expect(request -> { }).andRespond(withSuccess(
                "{\"documents\":[{\"title\":\"책\",\"authors\":[\"저자\"],\"isbn\":\"8996094013 9788996094012\","
                        + "\"datetime\":\"2023-01-01T00:00:00.000+09:00\"}],\"meta\":{\"total_count\":41}}",
                MediaType.APPLICATION_JSON));

        SearchResponse response = kakao.search(Category.BOOK, "책", 1);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getExternalId()).isEqualTo("9788996094012");
        assertThat(response.getTotalResults()).isEqualTo(41);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(logs.list).isEmpty();
    }

    /** 메시지 + (있다면) 스택트레이스까지 포함한 로그 전체 텍스트 */
    private String allLogText() {
        return logs.list.stream()
                .map(e -> e.getFormattedMessage()
                        + (e.getThrowableProxy() != null ? ThrowableProxyUtil.asString(e.getThrowableProxy()) : ""))
                .collect(Collectors.joining("\n"));
    }
}
