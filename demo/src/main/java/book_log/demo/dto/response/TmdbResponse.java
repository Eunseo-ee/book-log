package book_log.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class TmdbResponse {
    private List<TmdbItem> results; // 검색 결과 리스트

    // TMDB에서 result 안에 상세 정보를 리스트 형태로 담아놓은 구조를 자바 객체로 똑같이 복제하기 위해 TmdbResponse 안에 TmdbItem 넣음
    @Getter @Setter
    public static class TmdbItem {
        private Long id;
        
        @JsonProperty("media_type")
        private String mediaType; // movie 또는 tv
        
        private String title;       // 영화 제목
        private String name;        // TV 제목
        
        @JsonProperty("poster_path")
        private String posterPath;
        
        @JsonProperty("release_date")
        private String releaseDate; // 영화 개봉일
        
        @JsonProperty("first_air_date")
        private String firstAirDate; // TV 첫 방영일
        
        @JsonProperty("vote_average")
        private Double voteAverage;  // 평점
        
        private String overview;     // 줄거리
    }
}
