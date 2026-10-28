package book_log.demo.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import book_log.demo.domain.Category;
import book_log.demo.dto.response.SearchResponse;
import book_log.demo.dto.response.UnifiedSearchResponse;
import book_log.demo.service.ContentService.SavedKey;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final List<SearchProvider> providers;
    private final ContentService contentService;

    // 캐싱은 여기(조합 결과)가 아니라 Provider 단위에서 한다. (TmdbSearchService.search 참고)
    // isSaved는 '사용자의 DB 상태'라서 10분 TTL 캐시에 넣으면 낡은 값이 보인다.
    // 그래서 캐시를 거친 Provider 결과에 대해, 캐시 바깥(이 메서드)에서 매 요청마다 새로 채운다.
    public SearchResponse search(Long memberId, Category category, String query, int page) {
        SearchResponse response = searchProviders(category, query, page);
        return markSavedStatus(memberId, response);
    }

    // 저장 여부를 한 번의 IN 쿼리로 조회해서 결과마다 채운다.
    // 페이지 정보는 그대로 두고 results만 교체한 새 SearchResponse를 만든다.
    private SearchResponse markSavedStatus(Long memberId, SearchResponse response) {
        List<UnifiedSearchResponse> results = response.getResults();
        if (results == null || results.isEmpty()) {
            return response;
        }

        List<String> externalIds = results.stream()
                .map(UnifiedSearchResponse::getExternalId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Set<SavedKey> savedKeys = contentService.findSavedKeys(memberId, externalIds);

        List<UnifiedSearchResponse> marked = results.stream()
                .map(r -> r.toBuilder()
                        .isSaved(savedKeys.contains(new SavedKey(r.getExternalId(), r.getCategory())))
                        .build())
                .toList();

        return SearchResponse.builder()
                .results(marked)
                .currentPage(response.getCurrentPage())
                .totalPages(response.getTotalPages())
                .totalResults(response.getTotalResults())
                .build();
    }

    // Provider들을 호출해 검색 결과를 모으는 로직 (캐시 대상 구간)
    private SearchResponse searchProviders(Category category, String query, int page) {
        // 1. 통합 검색 (ALL)인 경우
        if (category == Category.ALL) {
            return searchAll(query, page);
        }

        // 2. 애니메이션 검색인 경우 (TMDB의 결과 리스트만 활용)
        if (category == Category.ANIME) {
            SearchResponse tmdbResponse = providers.stream()
                    .filter(p -> p.supports(Category.MOVIE) || p.supports(Category.TV))
                    .findFirst() // TMDB Provider 선택
                    .map(p -> p.search(category, query, page))
                    .orElseThrow(() -> new IllegalArgumentException("애니메이션 검색을 지원하는 서비스가 없습니다."));

            // 리스트 필터링 후 다시 SearchResponse로 포장
            List<UnifiedSearchResponse> filteredResults = tmdbResponse.getResults().stream()
                    .filter(r -> r.getCategory() == Category.ANIME_MOVIE || r.getCategory() == Category.ANIME_TVA)
                    .collect(Collectors.toList());

            return SearchResponse.builder()
                    .results(filteredResults)
                    .totalPages(tmdbResponse.getTotalPages())
                    .totalResults(tmdbResponse.getTotalResults()) // 실제로는 필터링되어 더 적겠지만 메타데이터는 유지
                    .build();
        }

        // 3. 그 외 (BOOK, MOVIE, TV 등) 검색
        return providers.stream()
                .filter(p -> p.supports(category))
                .findFirst()
                .map(p -> p.search(category, query, page))
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 카테고리입니다: " + category));
    }

    private SearchResponse searchAll(String query, int page) {
        // 모든 제공자의 응답을 리스트로 먼저 받습니다.
        List<SearchResponse> responses = providers.stream()
                .map(p -> p.search(Category.ALL, query, page))
                .collect(Collectors.toList());

        // 1. 결과 데이터 합치기 및 정렬
        List<UnifiedSearchResponse> combinedResults = responses.stream()
                .flatMap(r -> r.getResults().stream()) // 각 응답의 리스트를 꺼내서 합침
                .sorted(Comparator.comparing(UnifiedSearchResponse::getVoteAverage, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        // 2. 메타데이터 합치기 (전체 개수 합산, 최대 페이지 수 계산)
        long totalResults = responses.stream().mapToLong(SearchResponse::getTotalResults).sum();
        int maxTotalPages = responses.stream().mapToInt(SearchResponse::getTotalPages).max().orElse(1);

        return SearchResponse.builder()
                .results(combinedResults)
                .totalPages(maxTotalPages)
                .totalResults(totalResults)
                .build();
    }
}