package book_log.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import book_log.demo.config.ApiConfig;
import book_log.demo.domain.Category;

/**
 * 외부 API로 실제 나가는 요청 URI를 검증한다 (진짜 RestTemplate + MockRestServiceServer, 네트워크 없음).
 * 검색어가 쿼리 파라미터 '값 하나'로 정확히 전달되는지(분리/잘림/이중 인코딩이 없는지)를 본다.
 */
class ExternalApiUrlEncodingTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private KakaoSearchService kakao;
    private TmdbSearchService tmdb;

    static Stream<String> queries() {
        return Stream.of(
                "해리 포터",            // 한글 + 공백
                "a&b",                 // & : 파라미터 구분자
                "c#sharp",             // # : fragment 시작
                "100%",                // % : 인코딩 시작 문자
                "%EC%95%88",           // 이미 인코딩처럼 보이는 문자열은 '글자 그대로' 검색해야 한다
                "a+b",                 // + : 공백으로 해석될 수 있음
                "a=b?c/d",             // = ? /
                "  앞뒤 공백  ",
                "😀 이모지");
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();

        ApiConfig apiConfig = mock(ApiConfig.class);
        when(apiConfig.getKakaoKey()).thenReturn("test-kakao-key");
        when(apiConfig.getTmdbToken()).thenReturn("test-tmdb-token");

        kakao = new KakaoSearchService(restTemplate, apiConfig);
        tmdb = new TmdbSearchService(restTemplate, apiConfig);
    }

    @ParameterizedTest(name = "Kakao: [{0}]")
    @MethodSource("queries")
    @DisplayName("Kakao: 검색어가 query 파라미터 하나로 한 번만 인코딩되어 전달된다")
    void kakaoQueryIsEncodedOnce(String query) {
        AtomicReference<URI> sent = new AtomicReference<>();
        server.expect(request -> sent.set(request.getURI()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "KakaoAK test-kakao-key"))
                .andRespond(withSuccess("{\"documents\":[],\"meta\":{\"total_count\":0}}", MediaType.APPLICATION_JSON));

        kakao.search(Category.BOOK, query, 1);

        server.verify();
        assertThat(sent.get().getHost()).isEqualTo("dapi.kakao.com");
        assertThat(sent.get().getPath()).isEqualTo("/v3/search/book");
        assertThat(sent.get().getRawFragment()).isNull();
        assertQueryParamRoundTrips(sent.get(), "query", query);
    }

    @ParameterizedTest(name = "TMDB: [{0}]")
    @MethodSource("queries")
    @DisplayName("TMDB: 검색어가 query 파라미터 하나로 한 번만 인코딩되어 전달되고 language/page는 유지된다")
    void tmdbQueryIsEncodedOnce(String query) {
        AtomicReference<URI> sent = new AtomicReference<>();
        server.expect(request -> sent.set(request.getURI()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-tmdb-token"))
                .andRespond(withSuccess("{\"results\":[],\"total_pages\":1,\"total_results\":0}", MediaType.APPLICATION_JSON));

        tmdb.search(Category.MOVIE, query, 3);

        server.verify();
        assertThat(sent.get().getHost()).isEqualTo("api.themoviedb.org");
        assertThat(sent.get().getPath()).isEqualTo("/3/search/multi");
        assertThat(sent.get().getRawFragment()).isNull();
        assertQueryParamRoundTrips(sent.get(), "query", query);
        assertThat(UriComponentsBuilder.fromUri(sent.get()).build().getQueryParams().get("language")).containsExactly("ko-KR");
        assertThat(UriComponentsBuilder.fromUri(sent.get()).build().getQueryParams().get("page")).containsExactly("3");
    }

    /** raw query에서 해당 파라미터가 정확히 하나이고, 한 번만 디코딩하면 원래 검색어와 같아야 한다. */
    private static void assertQueryParamRoundTrips(URI uri, String name, String expected) {
        List<String> values = UriComponentsBuilder.fromUri(uri).build().getQueryParams().get(name);
        assertThat(values).as("raw query: %s", uri.getRawQuery()).hasSize(1);
        // 서버가 폼 방식으로 읽으면 '+'는 공백이 된다. 값 안의 + & # 공백은 반드시 %XX로 나가야 한다
        assertThat(values.get(0)).doesNotContain("+", "&", "#", " ");
        assertThat(UriUtils.decode(values.get(0), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(expected);
    }
}
