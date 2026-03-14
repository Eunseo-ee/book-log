package book_log.demo.service;

import book_log.demo.domain.Category;
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

    @Value("${api.kakao.key}")
    private String kakaoKey;

    // ???? 이거 왜 하는건지 ??
    @Override
    public boolean supports(Category category) {
        return category==Category.BOOK;
    }

    @Override
    public List<UnifiedSearchResponse> search(String query) {
        // 1. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK "+kakaoKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. URL 설정
        String url = "https://dapi.kakao.com/v3/search/book?query="+query;

        // 3. API 호출
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        return mapToResponse(response.getBody());
    }

    private List<UnifiedSearchResponse> mapToResponse(Map<String, Object> body) {
        List<Map<String, Object>> documents=(List<Map<String, Object>>) body.get("documents");

        return documents.stream().map(doc -> {
            List<String> authors = (List<String>) doc.get("authors");
            String author = (authors !=null && !authors.isEmpty()) ? authors.get(0) : "저자 미상";

            return UnifiedSearchResponse.builder()
                    .title((String) doc.get("title"))
                    .authorOrDirector(author)
                    .thumbnailUrl((String) doc.get("thumbnail"))
                    .category(Category.BOOK)
                    .build();
        }).collect(Collectors.toList());
    }
}
