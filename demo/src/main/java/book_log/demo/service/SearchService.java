package book_log.demo.service;

import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import book_log.demo.domain.Category;
import book_log.demo.dto.response.SearchResponse;
import book_log.demo.dto.response.UnifiedSearchResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final List<SearchProvider> providers;

    public SearchResponse search(Category category, String query, int page) {
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