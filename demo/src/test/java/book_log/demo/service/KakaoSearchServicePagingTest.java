package book_log.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import book_log.demo.config.ApiConfig;
import book_log.demo.domain.Category;
import book_log.demo.dto.response.SearchResponse;

/**
 * Kakao 페이지네이션: 요청에 page/size가 실제로 실리는지, totalPages가 같은 PAGE_SIZE로 올림 계산되는지 확인한다.
 * 진짜 RestTemplate + MockRestServiceServer (네트워크 없음).
 */
class KakaoSearchServicePagingTest {

    private MockRestServiceServer server;
    private KakaoSearchService kakao;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        ApiConfig apiConfig = mock(ApiConfig.class);
        when(apiConfig.getKakaoKey()).thenReturn("test-key");
        kakao = new KakaoSearchService(restTemplate, apiConfig);
    }

    private static String body(int totalCount) {
        return "{\"documents\":[],\"meta\":{\"total_count\":" + totalCount + "}}";
    }

    static Stream<Arguments> totalPagesCases() {
        return Stream.of(
                Arguments.of(0, 0),       // zero-state: 기존 동작 유지(ceil(0/20)=0, 프론트가 max(,1)로 표시)
                Arguments.of(1, 1),
                Arguments.of(20, 1),      // 딱 나누어떨어지면 올림 없음
                Arguments.of(21, 2),      // 나머지 1건 -> 올림
                Arguments.of(40, 2),
                Arguments.of(41, 3),
                Arguments.of(1000, 50),   // 50 * 20 = 상한과 정확히 일치
                Arguments.of(1001, 50),   // 상한으로 제한
                Arguments.of(50000, 50));
    }

    @ParameterizedTest(name = "total_count={0} -> totalPages={1}")
    @MethodSource("totalPagesCases")
    @DisplayName("totalPages는 total_count를 PAGE_SIZE(20)로 올림 나눈 값이고 최대 50이다")
    void totalPages(int totalCount, int expectedPages) {
        server.expect(request -> { }).andRespond(withSuccess(body(totalCount), MediaType.APPLICATION_JSON));

        SearchResponse response = kakao.search(Category.BOOK, "책", 1);

        assertThat(response.getTotalPages()).isEqualTo(expectedPages);
        assertThat(response.getTotalResults()).isEqualTo(totalCount);
    }

    @ParameterizedTest(name = "page={0}")
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2, 7, 50})
    @DisplayName("요청한 page와 size=PAGE_SIZE가 Kakao 요청에 그대로 전달된다")
    void pageAndSizeAreSent(int page) {
        AtomicReference<URI> sent = new AtomicReference<>();
        server.expect(request -> sent.set(request.getURI()))
                .andRespond(withSuccess(body(100), MediaType.APPLICATION_JSON));

        kakao.search(Category.BOOK, "책", page);

        var params = UriComponentsBuilder.fromUri(sent.get()).build().getQueryParams();
        assertThat(params.getFirst("page")).isEqualTo(String.valueOf(page));
        assertThat(params.getFirst("size")).isEqualTo(String.valueOf(KakaoSearchService.PAGE_SIZE));
        assertThat(KakaoSearchService.PAGE_SIZE).isEqualTo(20);
    }

    @ParameterizedTest(name = "page={0}")
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, -3})
    @DisplayName("page가 1 미만이면 1로 보정해서 보낸다")
    void pageBelowOneIsClampedToOne(int page) {
        AtomicReference<URI> sent = new AtomicReference<>();
        server.expect(request -> sent.set(request.getURI()))
                .andRespond(withSuccess(body(100), MediaType.APPLICATION_JSON));

        kakao.search(Category.BOOK, "책", page);

        assertThat(UriComponentsBuilder.fromUri(sent.get()).build().getQueryParams().getFirst("page")).isEqualTo("1");
    }

    @Test
    @DisplayName("page가 상한(50)을 넘으면 Kakao를 호출하지 않고 빈 결과 + totalPages 50을 돌려준다")
    void pageAboveMaxDoesNotCallKakao() {
        // server에 expect를 등록하지 않았으므로 호출되면 AssertionError가 난다
        SearchResponse response = kakao.search(Category.BOOK, "책", 51);

        assertThat(response.getResults()).isEmpty();
        assertThat(response.getTotalPages()).isEqualTo(50);
        assertThat(response.getTotalResults()).isZero();
        server.verify();
    }
}
