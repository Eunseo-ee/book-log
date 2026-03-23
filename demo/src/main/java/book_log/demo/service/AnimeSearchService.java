package book_log.demo.service;

import book_log.demo.domain.Category;
import book_log.demo.dto.response.AnimeResponse;
import book_log.demo.dto.response.UnifiedSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnimeSearchService implements SearchProvider {
    
    private final RestTemplate restTemplate;

    @Override
    public boolean supports(Category category) {
        return category == Category.ANIME || category == Category.ALL;
    }

    @Override
    public List<UnifiedSearchResponse> search(Category category, String query) {
        String url = "https://graphql.anilist.co";

        // 1. GraphQL 쿼리 (텍스트블록 이용)
        String graphqlQuery = """
            query ($search: String) {
              Page(perPage: 10) {
                media(search: $search, type: ANIME) {
                  id
                  title { romaji english native }
                  coverImage { large }
                  averageScore
                  startDate { year month day }
                }
              }
            }
            """;

        // 2. 요청 바디 구성(Map 이용해 JSON 구조 생성)
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", graphqlQuery);
        requestBody.put("variables", Collections.singletonMap("search", query)); 

        // 3. 헤더 설정 (JSON 타입 명시)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // 4. API 호출 (GraphQL은 조회도 POST를 주로 사용)
        ResponseEntity<AnimeResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, AnimeResponse.class);

        return mapToResponse(response.getBody(), category);
    }

    private List<UnifiedSearchResponse> mapToResponse(AnimeResponse body, Category category) {
        if (body == null || body.getData()==null || body.getData().getPage() == null) {
            return List.of();
        }

        return body.getData().getPage().getMedia().stream()
                .map(media -> {
                    // 1. 제목 결정 (한국어(native)->영어(english)->로마자(romaji) 순서)
                    String title = media.getTitle().getNativeTitle(); // 일단 한국어 시도

                    if (title == null || title.isEmpty()) {
                        title = media.getTitle().getEnglish(); // 없으면 영어
                    }

                    if (title == null || title.isEmpty()) {
                        title = media.getTitle().getRomaji(); // 그것도 없으면 로마지
                    }

                    // 날짜 가공 (YYYY-MM-DD)
                    String date = "날짜 정보 없음";
                    if (media.getStartDate() != null && media.getStartDate().getYear() != null) {
                        date = String.format("%d-%02d-%02d",
                                media.getStartDate().getYear(),
                                (media.getStartDate().getMonth() != null ? media.getStartDate().getMonth() : 1),
                                (media.getStartDate().getDay() != null ? media.getStartDate().getDay() : 1));
                    }

                    // 평점 정규화 : 100점 만점을 10.0 만점으로 변환
                    Double score = (media.getAverageScore() != null) ? media.getAverageScore() / 10.0 : null;

                    return UnifiedSearchResponse.builder()
                            .title(title)
                            .authorOrDirector("AniList") // 애니는 감독 정보가 계층이 너무 깊어 출처로 대체
                            .releaseDate(date)
                            .voteAverage(score)
                            .thumbnailUrl(media.getCoverImage().getLarge())
                            .category(Category.ANIME)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
