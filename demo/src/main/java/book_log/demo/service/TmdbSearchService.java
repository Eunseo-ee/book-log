package book_log.demo.service;

import book_log.demo.config.ApiConfig;
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
@RequiredArgsConstructor
public class TmdbSearchService implements SearchProvider {
    
    private final RestTemplate restTemplate;
    private final ApiConfig apiConfig;

    @Override
    public boolean supports(Category category) {
        return category == Category.DRAMA || category == Category.MOVIE;
    }

    @Override
    public List<UnifiedSearchResponse> search(Category category, String query) {
        // 1. 헤더 설정
        HttpHeaders headers = new HttpHeaders();

        // TMDB는 'Bearer' 방식 사용
        headers.set("Authorization", "Bearer " + apiConfig.getTmdbToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. URL 설정 (영화/드라마 구분해야함)
        // 카테고리에 따라 movie 또는 tv로 경로 변경
        String type = (category == category.MOVIE) ? "movie" : "tv";

        String url = "https://api.themoviedb.org/3/search/"+type
                        +"?query="+query
                        +"&language=ko-KR";
        
        // 3. API 호출
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        return mapToResponse(response.getBody(), category);
    }

    private List<UnifiedSearchResponse>mapToResponse(Map<String, Object> body, Category category) {
        // TMDB는 results 라는 이름으로 목록 줌
        List<Map<String, Object>> results=(List<Map<String, Object>>) body.get("results");

        return results.stream().map(res -> {
            // 영화는 title, 드라마는 name 필드 사용
            String title = (category == Category.MOVIE)
                            ? (String) res.get("title")
                            : (String) res.get("name");
            
            // 포스터 경로는 앞에 tmdb 기본 url 붙여야 이미지가 보임
            String posterPath = (String) res.get("poster_path");
            String fullTumbnailUrl = (posterPath != null)
                                        ? "https://image.tmdb.org/t/p/w500" + posterPath
                                        : null;

            return UnifiedSearchResponse.builder()
                    .title(title)
                    .authorOrDirector("상세 정보 참조") // 필요 시 감독 정보를 따로 가져오는 로직 추가 가능
                    .thumbnailUrl(fullTumbnailUrl)
                    .category(category)
                    .build();
        }).collect(Collectors.toList());
    }
}
