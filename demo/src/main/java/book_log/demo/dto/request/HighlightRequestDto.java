package book_log.demo.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HighlightRequestDto {

    @NotBlank(message = "하이라이트 내용은 필수입니다.")
    @Size(max = 1000, message = "내용은 1000자 이내로 입력해주세요.")
    private String text; // 명대사/문장 (필수)

    @NotNull(message = "연관된 콘텐츠 ID는 필수입니다.")
    private Long contentId; // 부모 콘텐츠 ID(필수)

    // 선택적 필드 (null 허용)
    @Min(value = 1, message = "페이지는 1 이상이어야 합니다.")
    private Integer page; // 도서용 페이지
    
    private Integer season; // 드라마/애니용 기수
    private Integer episode; // 드라마/애니용 화수
    private String timestamp; // 영화/영상용 시간대 (ex. "01:23:45")
}
