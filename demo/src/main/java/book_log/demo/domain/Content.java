package book_log.demo.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Getter 
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 기본 생성자 접근 제한
@AllArgsConstructor // 빌더를 위한 전체 생성자
@Builder
public class Content extends BaseTimeEntity{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Category category;

    // 추후 추가할 API의 id에 문자 섞여있을 경우 대비
    private String externalId;
    private String title;
    private String authorOrDirector;
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Double rating;
    private LocalDate viewDate;

    @Column(columnDefinition = "TEXT")
    private String comment; // 사용자의 상세 감상평 (길어질 수 있으므로 TEXT)

    private String genre;

    private Integer runtime; // 영화/애니의 경우 상영 시간(분), 도서의 경우 페이지 수

    // 비즈니스 로직 : 상태나 평점을 변경해야 할 때 사용
    public void updateStatus(Status status) {
        this.status = status;
    }

    public void updateRating(Double rating) {
        this.rating = rating;
    }

    public void updateComment(String comment) {
        this.comment = comment;
    }
}
