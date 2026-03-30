package book_log.demo.dto.response;

import java.time.LocalDate;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import lombok.Getter;

@Getter
public class ContentResponseDto {
    private Long id;
    private String title;
    private String author;
    private Category category;
    private LocalDate viewDate;
    private Double rating;
    private String imgUrl;

    // 엔티티를 DTO로 변환하는 생성자
    public ContentResponseDto(Content entity) {
        this.id = entity.getId();
        this.title = entity.getTitle();
        this.author = entity.getAuthorOrDirector();
        this.category = entity.getCategory();
        this.viewDate = entity.getViewDate();
        this.rating = entity.getRating();
        this.imgUrl = entity.getThumbnailUrl();
    }
}
