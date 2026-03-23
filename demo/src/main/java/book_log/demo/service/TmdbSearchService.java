package book_log.demo.service;

import book_log.demo.config.ApiConfig;
import book_log.demo.domain.Category;
import book_log.demo.dto.response.TmdbResponse;
import book_log.demo.dto.response.UnifiedSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TmdbSearchService implements SearchProvider {
    
    private final RestTemplate restTemplate;
    private final ApiConfig apiConfig;

    @Override
    public boolean supports(Category category) {
        return category == Category.TV || category == Category.MOVIE || category == Category.ALL;
    }

    @Override
    public List<UnifiedSearchResponse> search(Category category, String query) {
        // 1. 헤더 설정
        HttpHeaders headers = new HttpHeaders();

        // TMDB는 'Bearer' 방식 사용
        headers.set("Authorization", "Bearer " + apiConfig.getTmdbToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. Multi 검색 URL 설정 (영화/드라마 구분 없이 한번에 검색)
        String url = "https://api.themoviedb.org/3/search/multi?query="
                        +query+"&language=ko-KR";
        
        // 3. API 호출
        ResponseEntity<TmdbResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, TmdbResponse.class);

        return mapToResponse(response.getBody(), category);
    }

    private List<UnifiedSearchResponse>mapToResponse(TmdbResponse body, Category category) {

        if (body==null || body.getResults()==null) return List.of();

        return body.getResults().stream()
            // 인물 (person) 결과는 제외하고 영화와 드라마만 필터링
            .filter(item->"movie".equals(item.getMediaType()) || "tv".equals(item.getMediaType()))
            .map(item -> {
                boolean isMovie = "movie".equals(item.getMediaType());

                // 1. 제목 : 영화는 title, 드라마는 name 필드 사용
                String title = isMovie ? item.getTitle() : item.getName();
                
                // 2. 개봉일/방영일 : 영환s release_date, 드라마는 first_air_date
                String date = isMovie ? item.getReleaseDate() : item.getFirstAirDate();

                // 3. 실제 아이템의 카테고리 결정 (아이템 자체의 mediaType 우선)
                Category itemCategory = isMovie ? Category.MOVIE : Category.TV;

                // 4. 포스터 이미지
                // 포스터 경로는 앞에 tmdb 기본 url 붙여야 이미지가 보임
                String fullThumbnailUrl = (item.getPosterPath() != null)
                                            ? "https://image.tmdb.org/t/p/w500" + item.getPosterPath()
                                            : null;

                return UnifiedSearchResponse.builder()
                        .title(title)
                        .authorOrDirector("상세 정보 참조")
                        .releaseDate(date)
                        .voteAverage(item.getVoteAverage())
                        .thumbnailUrl(fullThumbnailUrl)
                        .category(itemCategory)
                        .build();
            }).collect(Collectors.toList());
    }
}
