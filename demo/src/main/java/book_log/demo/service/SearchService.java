package book_log.demo.service;

import java.util.List;

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
        return providers.stream()
                .filter(p -> p.supports(category)) // 1. 현재 카테고리를 지원하는 Service 찾음
                .findFirst() // 2. 가장 먼저 매칭되는 Service 선택
                .map(p -> p.search(query)) // 3. 해당 담당자의 검색 로직 실행
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 카테고리입니다: "+category));
    }
}
