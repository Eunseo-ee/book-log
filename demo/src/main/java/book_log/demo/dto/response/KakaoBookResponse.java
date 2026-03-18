package book_log.demo.dto.response;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class KakaoBookResponse {
    private List<Document> documents;

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
}
