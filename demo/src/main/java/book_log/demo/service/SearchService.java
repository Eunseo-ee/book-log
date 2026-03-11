package book_log.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import book_log.demo.dto.response.UnifiedSearchResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor

public class SearchService {
    private final KakaoSearchService kakaoSearchService;
    private final TmdbSearchService tmdbSearchService;
    private final AnimeSearchService animeSearchService;

    public List<UnifiedSearchResponse> search(String category, String query) {
        return switch (category.toUpperCase()) {
            case "BOOK" -> kakaoSearchService.search(query);
            case "MOVIE" -> tmdbSearchService.search(query);
            case "DRAMA" -> animeSearchService.search(query);
            case "ANIME" -> animeSearchService.search(query);
            default -> throw new IllegalArgumentException("지원하지 않는 카테고리입니다.");
        };
    }
}
