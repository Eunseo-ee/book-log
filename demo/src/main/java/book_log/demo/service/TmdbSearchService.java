package book_log.demo.service;

import book_log.demo.config.ApiConfig;
import book_log.demo.domain.Category;
import book_log.demo.dto.response.TmdbResponse;
import book_log.demo.dto.response.UnifiedSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
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

    // [캐시 정책]
    // - key는 query만 사용: TMDB 호출(search/multi)과 매핑 결과가 category와 무관하므로
    //   ALL/MOVIE/TV/ANIME 검색이 같은 캐시 항목을 공유한다.
    //   (나중에 category별로 결과가 달라지도록 바꾼다면 key에 category를 다시 포함해야 함)
    // - cacheNames를 Provider 전용("tmdbSearch")으로 분리: 다른 Provider를 캐싱해도 키가 충돌하지 않게 함
    // - 빈 리스트는 캐싱 제외(unless): 장애 시 아래 catch에서 emptyList()를 반환하므로,
    //   그대로 캐싱되면 TTL(10분) 동안 장애 결과가 고정된다.
    @Cacheable(cacheNames = "tmdbSearch", key = "#query", unless = "#result == null || #result.isEmpty()")
    @Override
    public List<UnifiedSearchResponse> search(Category category, String query) {
        // 1. 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiConfig.getTmdbToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. Multi 검색 URL 설정
        String url = "https://api.themoviedb.org/3/search/multi?query=" + query + "&language=ko-KR";

        try {
            // 3. API 호출 (타임아웃 발생 시 catch로 이동)
            ResponseEntity<TmdbResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, TmdbResponse.class);

            // 4. 응답 데이터 검증 (결과가 아예 없거나 body가 null인 경우)
            if (response.getBody() == null || response.getBody().getResults() == null) {
                return Collections.emptyList();
            }

            // 5. 정상 매핑 및 반환
            return mapToResponse(response.getBody(), category);

        } catch (Exception e) {
            // 🚨 API 서버 장애, 타임아웃, 네트워크 단절 시 실행됨
            log.error("TMDB API 호출 중 예외 발생 [query: {}]: {}", query, e.getMessage());
            
            // 에러가 나도 서버를 중단하지 않고 빈 리스트를 반환하여 서비스 유지
            return Collections.emptyList();
        }
    }

    private List<UnifiedSearchResponse>mapToResponse(TmdbResponse body, Category category) {

        if (body==null || body.getResults()==null) return List.of();

        return body.getResults().stream()
            // 인물 (person) 결과는 제외하고 영화와 드라마만 필터링
            .filter(item->"movie".equals(item.getMediaType()) || "tv".equals(item.getMediaType()))
            .map(item -> {
                boolean isMovie = "movie".equals(item.getMediaType());

                // Genre ID 리스트에 16(애니메이션)이 포함되어 있는지 확인
                boolean isAnimation = item.getGenreIds() != null && item.getGenreIds().contains(16);

                // 카테고리 지정
                Category itemCategory;
                if (isAnimation) {
                    // 애니메이션인 경우
                    itemCategory = isMovie ? Category.ANIME_MOVIE : Category.ANIME_TVA;
                } else {
                    // 실사인 경우
                    itemCategory = isMovie ? Category.MOVIE : Category.TV;
                }
                // 1. 제목 : 영화는 title, 드라마는 name 필드 사용
                String title = isMovie ? item.getTitle() : item.getName();
                
                // 2. 개봉일/방영일 : 영화는 release_date, 드라마는 first_air_date
                String date = isMovie ? item.getReleaseDate() : item.getFirstAirDate();

                // 3. 포스터 이미지
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
