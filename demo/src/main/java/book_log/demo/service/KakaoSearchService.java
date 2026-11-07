package book_log.demo.service;

import book_log.demo.config.ApiConfig;
import book_log.demo.domain.Category;
import book_log.demo.dto.response.KakaoBookResponse;
import book_log.demo.dto.response.SearchResponse;
import book_log.demo.dto.response.UnifiedSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor // final 생성자 자동 생성
public class KakaoSearchService implements SearchProvider {
    
    private static final String BOOK_SEARCH_URL = "https://dapi.kakao.com/v3/search/book";

    private final RestTemplate restTemplate;
    private final ApiConfig apiConfig;

    @Override
    public boolean supports(Category category) {
        return category==Category.BOOK || category == Category.ALL;
    }

    // 형식 맞게 API 요청 링크 세팅 -> 요청 전송
    @Override
    public SearchResponse search(Category category, String query, int page) {
        // 1. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK "+apiConfig.getKakaoKey());
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. URL 설정: 검색어는 문자열 연결이 아니라 URI 변수로 넣고 .encode()로 값 전체를 인코딩한다.
        //    (& # + 같은 문자가 파라미터 구분자/fragment로 해석되지 않는다.)
        //    완성된 URI 객체를 넘기므로 RestTemplate이 한 번 더 인코딩하지 않는다.
        URI uri = UriComponentsBuilder.fromUriString(BOOK_SEARCH_URL)
                .queryParam("query", "{query}")
                .encode()
                .buildAndExpand(query)
                .toUri();

        try {
            // 3. API 호출
            ResponseEntity<KakaoBookResponse> response = restTemplate.exchange(uri, HttpMethod.GET, entity, KakaoBookResponse.class);
            KakaoBookResponse body = response.getBody();

            // 전체 페이지 계산 (카카오 응답의 total_count 활용)
            int totalCount = body.getMeta().getTotalCount();
            int totalPages = (int) Math.ceil((double) totalCount / 20);

            return SearchResponse.builder()
                .results(mapToResponse(body, category))
                .totalPages(totalPages)
                .totalResults(totalCount)
                .build();

        } catch (Exception e) {
            // TmdbSearchService와 같은 정책: 외부 API 실패(4xx/5xx/타임아웃/파싱 실패/빈 응답)는 규격에 맞는 빈 응답으로 바꾼다.
            // 로그에는 예외 종류와 메시지만 남긴다. Authorization 헤더는 요청 객체에만 있고 RestTemplate 예외 메시지에는 들어가지 않는다.
            log.error("Kakao API 호출 중 예외 발생 [{}]: {}", e.getClass().getSimpleName(), e.getMessage());

            return SearchResponse.builder()
                    .results(Collections.emptyList())
                    .totalPages(1)
                    .totalResults(0)
                    .build();
        }
    }

    private List<UnifiedSearchResponse> mapToResponse(KakaoBookResponse body, Category category) {
        if (body == null || body.getDocuments() == null) return List.of();

        // 데이터 꺼내서 우리 규격 객체로 변환->다시 리스트로 모음
        return body.getDocuments().stream()
                .map(doc -> {
                    // 저자 리스트를 "홍길동, 아무개" 형태의 문자열로 합치기
                    String authors = (doc.getAuthors() !=null && !doc.getAuthors().isEmpty())
                            ? String.join(", ", doc.getAuthors())
                            : "저자 미상";

                    // 날짜 형식 정리 (카카오 날짜는 보통 "2023-01-01T00:00:00.000+09:00")
                    // 앞부분 10자리(날짜)만 잘라서 사용
                    String date = (doc.getDatetime() != null && doc.getDatetime().length() >=10)
                            ? doc.getDatetime().substring(0,10)
                            : "날짜 정보 없음";

                    return UnifiedSearchResponse.builder()
                            .externalId(pickIsbn(doc.getIsbn()))
                            .title(doc.getTitle())
                            .authorOrDirector(authors)
                            .releaseDate(date)           // UnifiedSearchResponse에 추가한 필드 활용
                            .thumbnailUrl(doc.getThumbnail())
                            .category(Category.BOOK)
                            .build();
                }).collect(Collectors.toList());
            }

    /**
     * 카카오 isbn은 "ISBN10 ISBN13" 처럼 공백으로 구분되어 오며, 둘 중 하나만 올 수도 있다.
     * 저장 여부 판별용 externalId는 하나로 정해야 하므로 국제 표준인 ISBN13을 우선 사용하고,
     * ISBN13이 없을 때만 ISBN10을 그대로 쓴다. 값이 비어 있으면 null.
     */
    static String pickIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) return null;

        String[] tokens = isbn.trim().split("\\s+");
        for (String token : tokens) {
            if (token.length() == 13) return token;
        }
        return tokens[0];
    }
}
