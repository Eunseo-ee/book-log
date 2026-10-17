package book_log.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class TmdbResponse {
    private List<TmdbItem> results; // 검색 결과 리스트

    // 1. 전체 페이지 수 필드 추가
    @JsonProperty("total_pages")
    private int totalPages;

    // 2. 전체 결과 수 필드 추가
    @JsonProperty("total_results")
    private long totalResults;

    @Getter @Setter
    public static class TmdbItem {
        private Long id;
        
        @JsonProperty("media_type")
        private String mediaType; 
        
        private String title;       
        private String name;        

        @JsonProperty("genre_ids")
        private List<Integer> genreIds;
        
        @JsonProperty("poster_path")
        private String posterPath;
        
        @JsonProperty("release_date")
        private String releaseDate; 
        
        @JsonProperty("first_air_date")
        private String firstAirDate; 
        
        @JsonProperty("vote_average")
        private Double voteAverage;  
        
        private String overview;     
    }
}
