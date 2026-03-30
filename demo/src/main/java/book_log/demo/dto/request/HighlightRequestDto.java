package book_log.demo.dto.request;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HighlightRequestDto {
    private String text; // 명대사/문장 (필수)
    private Long contentId; // 부모 콘텐츠 ID(필수)

    // 선택적 필드 (null 허용)
    private Integer page; // 도서용 페이지
    private Integer season; // 드라마/애니용 기수
    private Integer episode; // 드라마/애니용 화수
    private String timestamp; // 영화/영상용 시간대 (ex. "01:23:45")
}
