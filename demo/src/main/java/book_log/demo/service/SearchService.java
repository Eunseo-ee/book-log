package book_log.demo.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import book_log.demo.domain.Category;
import book_log.demo.dto.response.UnifiedSearchResponse;
import book_log.demo.service.ContentService.SavedKey;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor

public class SearchService {
    // SearchProvider 인터페이스를 구현한 모든 클래스(Kakao, Tmdb, Anime 등)가 자동으로 리스트에 담김
    private final List<SearchProvider> providers;
    private final ContentService contentService;

    // 캐싱은 여기(조합 결과)가 아니라 Provider 단위에서 한다. (TmdbSearchService.search 참고)
    // ALL 검색도 내부에서 각 Provider를 호출하므로, TMDB 부분은 자동으로 캐시를 탄다.
    //
    // isSaved는 '사용자의 DB 상태'라서 10분 TTL 캐시에 넣으면 낡은 값이 보인다.
    // 그래서 캐시를 거친 Provider 결과에 대해, 캐시 바깥(이 메서드)에서 매 요청마다 새로 채운다.
    public List<UnifiedSearchResponse> search(Category category, String query) {
        List<UnifiedSearchResponse> results = searchProviders(category, query);
        return markSavedStatus(results);
    }

    // 저장 여부를 한 번의 IN 쿼리로 조회해서 결과마다 채운다.
    // 캐시에서 꺼낸 객체를 직접 수정하지 않고 toBuilder()로 복사본을 만든다 (원본은 캐시 항목일 수 있음).
    private List<UnifiedSearchResponse> markSavedStatus(List<UnifiedSearchResponse> results) {
        if (results.isEmpty()) {
            return results;
        }

        List<String> externalIds = results.stream()
                .map(UnifiedSearchResponse::getExternalId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Set<SavedKey> savedKeys = contentService.findSavedKeys(externalIds);

        return results.stream()
                .map(r -> r.toBuilder()
                        .isSaved(savedKeys.contains(new SavedKey(r.getExternalId(), r.getCategory())))
                        .build())
                .toList();
    }

    // Provider들을 호출해 검색 결과를 모으는 기존 로직 (캐시 대상 구간)
    private List<UnifiedSearchResponse> searchProviders(Category category, String query) {
        // 1. 통합 검색 (ALL)인 경우
        if (category == Category.ALL) {
            return searchAll(query);
        }

        // 2. 애니메이션 검색인 경우
        if (category == Category.ANIME) {
            return providers.stream()
                    .filter(p -> p.supports(Category.MOVIE) || p.supports(Category.TV)) // TMDB 소환
                    .flatMap(p -> p.search(category, query).stream())
                    .filter(r -> r.getCategory() == Category.ANIME_MOVIE || r.getCategory() == Category.ANIME_TVA)
                    .collect(Collectors.toList());
        }

        // 3. 그 외 (BOOK, MOVIE, TV 등) 검색
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
