package book_log.demo.service;

import book_log.demo.config.ApiConfig;
import book_log.demo.domain.Category;
import book_log.demo.dto.response.SearchResponse;
import book_log.demo.dto.response.TmdbResponse;
import book_log.demo.dto.response.UnifiedSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j // log 변수 사용할 수 있게 함
@Service
@RequiredArgsConstructor
public class TmdbSearchService implements SearchProvider {
    
    private final RestTemplate restTemplate;
    private final ApiConfig apiConfig;

    @Override
    public boolean supports(Category category) {
        return category == Category.TV || category == Category.MOVIE || category == Category.ANIME_MOVIE || category == Category.ANIME_TVA|| category == Category.ANIME || category == Category.ALL;
    }

    @Override
    public SearchResponse search(Category category, String query, int page) {
        // 1. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiConfig.getTmdbToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. Multi 검색 URL 설정
        String url = "https://api.themoviedb.org/3/search/multi?query=" + query + "&language=ko-KR&page=" + page;

        try {
            // 3. API 호출
            ResponseEntity<TmdbResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, TmdbResponse.class);
            TmdbResponse body = response.getBody();

            // 4. 응답 데이터 검증
            if (body == null || body.getResults() == null) {
                return SearchResponse.builder()
                        .results(Collections.emptyList())
                        .totalPages(1)
                        .totalResults(0)
                        .build();
            }

            // 5. 정상 매핑 및 반환 (SearchResponse 박스에 담아서 반환)
            return SearchResponse.builder()
                    .results(mapToResponse(body, category)) // 기존 mapToResponse 활용
                    .totalPages(body.getTotalPages())       // TMDB 응답의 total_pages 사용
                    .totalResults(body.getTotalResults())   // TMDB 응답의 total_results 사용
                    .build();

        } catch (Exception e) {
            log.error("TMDB API 호출 중 예외 발생 [query: {}]: {}", query, e.getMessage());
            
            // 에러 발생 시에도 규격에 맞는 빈 응답 반환
            return SearchResponse.builder()
                    .results(Collections.emptyList())
                    .totalPages(1)
                    .totalResults(0)
                    .build();
        }
    }

    private List<UnifiedSearchResponse> mapToResponse(TmdbResponse body, Category category) {
        if (body == null || body.getResults() == null) return List.of();

        return body.getResults().stream()
            // 1. 인물 제외 (영화, TV만 남김)
            .filter(item -> "movie".equals(item.getMediaType()) || "tv".equals(item.getMediaType()))
            .map(item -> {
                boolean isMovie = "movie".equals(item.getMediaType());
                boolean isAnimation = item.getGenreIds() != null && item.getGenreIds().contains(16);

                // 데이터의 실제 성격 파악
                Category itemCategory;
                if (isAnimation) {
                    itemCategory = isMovie ? Category.ANIME_MOVIE : Category.ANIME_TVA;
                } else {
                    itemCategory = isMovie ? Category.MOVIE : Category.TV;
                }

                // DTO 빌드 (기존 로직 유지)
                String title = isMovie ? item.getTitle() : item.getName();
                String date = isMovie ? item.getReleaseDate() : item.getFirstAirDate();
                String fullThumbnailUrl = (item.getPosterPath() != null)
                        ? "https://image.tmdb.org/t/p/w500" + item.getPosterPath() : null;

                return UnifiedSearchResponse.builder()
                        .title(title)
                        .authorOrDirector("상세 정보 참조")
                        .releaseDate(date)
                        .voteAverage(item.getVoteAverage())
                        .thumbnailUrl(fullThumbnailUrl)
                        .category(itemCategory)
                        .build();
            })
            // 2. 사용자가 선택한 카테고리에 맞는 결과만 최종 필터링
            .filter(dto -> isMatchingCategory(dto.getCategory(), category))
            .collect(Collectors.toList());
    }

    // 카테고리 매칭 로직 분리
    private boolean isMatchingCategory(Category itemCategory, Category requestCategory) {
        if (requestCategory == Category.ALL) return true; // 전체 검색이면 모두 통과
        
        // 애니메이션 통합 카테고리 요청 시 극장판/TVA 모두 포함
        if (requestCategory == Category.ANIME) {
            return itemCategory == Category.ANIME_MOVIE || itemCategory == Category.ANIME_TVA;
        }
        
        // 그 외 (MOVIE, TV, ANIME_MOVIE, ANIME_TVA 등) 정확히 일치하는 경우만
        return itemCategory == requestCategory;
    }
}
