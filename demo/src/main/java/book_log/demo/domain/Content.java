package book_log.demo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Getter @NoArgsConstructor

public class Content {
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
}
