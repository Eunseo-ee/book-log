package book_log.demo.dto.response;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@Getter @Setter
public class KakaoBookResponse {
    private List<Document> documents;
    private Meta meta;

    // 카카오 도서 API는 변수명과 JSON의 키값이 동일해서 JsonProperty 사용 X
    @Getter @Setter
    public static class Document {
        private String title;
        private List<String> authors;
        private String publisher;
        private String datetime;
        private String isbn;
        private Integer price;
        private String thumbnail;
        private String status;
    }

    @Getter
    @Setter
    public static class Meta {
        @JsonProperty("is_end")
        private boolean isEnd;

        @JsonProperty("pageable_count")
        private int pageableCount;

        @JsonProperty("total_count")
        private int totalCount;
    }
}
