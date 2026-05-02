package book_log.demo.dto.request;

import java.time.LocalDate;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.domain.Status;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentRequestDto {
    
    // 1. 외부 API에서 가져온 정보 (화면에서 hidden이나 데이터로 들고 있다가 넘겨줌)
    @NotBlank(message = "제목은 필수입니다.")
    private String title;
    private String authorOrDirector;
    private String thumbnailUrl;
    private String externalId;
    private Category category;
    private String genre;


    // 2. 사용자가 직접 입력한 '내 기록' (이게 핵심!)
    @NotNull(message = "관람 날짜를 선택해주세요.")
    private LocalDate viewDate;

    @Min(0) @Max(5)
    private Double rating;

    @Size(max = 500, message = "한줄평은 500자 이내로 작성해주세요.")
    private String comment; 
    
    private Status status; // WATCHING, COMPLETED 등

    public Content toEntity() {
        return Content.builder()
                .title(this.title)
                .authorOrDirector(this.authorOrDirector)
                .thumbnailUrl(this.thumbnailUrl)
                .externalId(this.externalId)
                .category(this.category)
                .genre(this.genre)
                .viewDate(this.viewDate) // 사용자가 입력한 날짜
                .rating(this.rating)     // 사용자가 입력한 별점
                .comment(this.comment)   // 사용자가 입력한 한줄평
                .status(this.status)     // 기본적으로 COMPLETED 등
                .build();
    }

}
