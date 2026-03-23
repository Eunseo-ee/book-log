package book_log.demo.service;

import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import book_log.demo.domain.Category;
import book_log.demo.dto.response.UnifiedSearchResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor

public class SearchService {
    // SearchProvider 인터페이스를 구현한 모든 클래스(Kakao, Tmdb, Anime 등)가 자동으로 리스트에 담김
    private final List<SearchProvider> providers;

    public List<UnifiedSearchResponse> search(Category category, String query) {
        // 1. 통합 검색 (ALL)인 경우
        if (category == Category.ALL) {
            return searchAll(query);
        }

        // 2. 개별 카테고리 검색인 경우 (기존 로직 유지)
        return providers.stream()
                .filter(p -> p.supports(category)) // 1. 현재 카테고리를 지원하는 Service 찾음
                .findFirst() // 2. 가장 먼저 매칭되는 Service 선택
                .map(p -> p.search(category, query)) // 3. 해당 담당자의 검색 로직 실행
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 카테고리입니다: "+category));
    }

    private List<UnifiedSearchResponse> searchAll(String query) {
        return providers.stream()
                // 모든 Provider의 검색 결과를 하나로 합침 (flatMap)
                // 그냥 map은 provider별로 리스트 각각 내는데 flatMap은 단일 리스트에 모음
                .flatMap(p -> p.search(Category.ALL, query).stream())
                // 평점이 높은 순으로 정렬 (평점이 null인 경우를 대비해 last 설정 가능)
                // 기본적으로 오름차순 정렬이라 다시 reverseOrder()
                .sorted(Comparator.comparing(UnifiedSearchResponse::getVoteAverage, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }
}
