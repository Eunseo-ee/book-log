package book_log.demo.dto.response;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class AnimeResponse {
    private Data data;

    @Getter @Setter
    public static class Data {
        private Page Page;
    }

    @Getter @Setter
    public static class Page {
        private List<Media> media;
    }

    @Getter @Setter
    public static class Media {
        private Long id;
        private  Title title;
        private CoverImage coverImage;
        private Double averageScore; // AniList는 100점 만점
        private StartDate startDate;

        @Getter @Setter
        public static class Title {
            private String romaji;
            private String english;
            // native는 자바 예약어라서 수정함
            @com.fasterxml.jackson.annotation.JsonProperty("native")
            private String nativeTitle;
        }

        @Getter @Setter
        public static class CoverImage {
            private String large;
        }

        @Getter @Setter
        public static class StartDate {
            private Integer year;
            private Integer month;
            private Integer day;
        }
    }
}
