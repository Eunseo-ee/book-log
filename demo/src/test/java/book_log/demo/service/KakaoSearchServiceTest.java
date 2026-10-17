package book_log.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import book_log.demo.config.ApiConfig;
import book_log.demo.domain.Category;
import book_log.demo.dto.response.KakaoBookResponse;
import book_log.demo.dto.response.SearchResponse;
import book_log.demo.dto.response.UnifiedSearchResponse;

@ExtendWith(MockitoExtension.class)
class KakaoSearchServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ApiConfig apiConfig;

    @InjectMocks
    private KakaoSearchService kakaoSearchService;

    static Stream<Arguments> isbnCases() {
        return Stream.of(
                Arguments.of("ISBN10, ISBN13이 둘 다 있으면 ISBN13 선택", "8996094013 9788996094012", "9788996094012"),
                Arguments.of("순서가 반대여도 길이로 ISBN13을 찾는다", "9788996094012 8996094013", "9788996094012"),
                Arguments.of("ISBN13만 있고 앞에 공백이 붙어 있어도 정상 처리", " 9788996094012", "9788996094012"),
                Arguments.of("ISBN10만 있으면 ISBN10을 그대로 사용", "8996094013", "8996094013"),
                Arguments.of("ISBN10 끝자리가 X여도 그대로 사용", "043942089X", "043942089X"),
                Arguments.of("빈 문자열이면 null", "", null),
                Arguments.of("공백뿐이면 null", "   ", null),
                Arguments.of("null이면 null", null, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("isbnCases")
    @DisplayName("카카오 isbn에서 externalId를 고른다")
    void externalId_fromIsbn(String ignoredName, String isbn, String expected) {
        KakaoBookResponse.Document doc = new KakaoBookResponse.Document();
        doc.setTitle("테스트 책");
        doc.setIsbn(isbn);

        // KakaoSearchService.search가 meta.total_count로 전체 페이지 수를 계산하므로 meta가 반드시 필요하다
        KakaoBookResponse.Meta meta = new KakaoBookResponse.Meta();
        meta.setTotalCount(1);

        KakaoBookResponse body = new KakaoBookResponse();
        body.setDocuments(List.of(doc));
        body.setMeta(meta);

        when(apiConfig.getKakaoKey()).thenReturn("test-key");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(KakaoBookResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        SearchResponse response = kakaoSearchService.search(Category.BOOK, "테스트", 1);

        assertThat(response.getResults()).hasSize(1);
        UnifiedSearchResponse result = response.getResults().get(0);
        assertThat(result.getExternalId()).isEqualTo(expected);
        // isSaved는 Provider(캐시 대상 구간)에서 채우지 않는다
        assertThat(result.getIsSaved()).isNull();
        assertThat(result.getCategory()).isEqualTo(Category.BOOK);
    }

    @Test
    @DisplayName("pickIsbn은 입력이 null이어도 예외 없이 null을 반환한다")
    void pickIsbn_null() {
        assertThat(KakaoSearchService.pickIsbn(null)).isNull();
    }
}