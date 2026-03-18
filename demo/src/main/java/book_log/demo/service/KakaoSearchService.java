package book_log.demo.service;

import book_log.demo.config.ApiConfig;
import book_log.demo.domain.Category;
import book_log.demo.dto.response.KakaoBookResponse;
import book_log.demo.dto.response.UnifiedSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor // final 생성자 자동 생성
public class KakaoSearchService implements SearchProvider {
    
    private final RestTemplate restTemplate;
    private final ApiConfig apiConfig;

    @Override
    public boolean supports(Category category) {
        return category==Category.BOOK;
    }

    // 형식 맞게 API 요청 링크 세팅 -> 요청 전송
    @Override
    public List<UnifiedSearchResponse> search(Category category, String query) {
        // 1. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK "+apiConfig.getKakaoKey());
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. URL 설정
        String url = "https://dapi.kakao.com/v3/search/book?query="+query;

        // 3. API 호출
        ResponseEntity<KakaoBookResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, KakaoBookResponse.class);

        return mapToResponse(response.getBody(), category);
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
                            .title(doc.getTitle())
                            .authorOrDirector(authors)
                            .releaseDate(date)           // UnifiedSearchResponse에 추가한 필드 활용
                            .thumbnailUrl(doc.getThumbnail())
                            .category(category)
                            .build();
                }).collect(Collectors.toList());
            }
}
