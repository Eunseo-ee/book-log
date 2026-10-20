package book_log.demo.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.domain.Status;

/**
 * 캘린더 조회(findActiveDays)는 user_id = 1 조건으로 거른다.
 * 저장 경로(toEntity → Content.builder())에서 userId가 null로 빠지지 않는지 서버/DB 없이 확인한다.
 */
class ContentRequestDtoTest {

    @Test
    @DisplayName("toEntity로 만든 Content의 userId는 기본값 1L이다")
    void toEntityKeepsDefaultUserId() {
        // given
        ContentRequestDto dto = ContentRequestDto.builder()
                .title("소년이 온다")
                .externalId("9788936434120")
                .category(Category.BOOK)
                .viewDate(LocalDate.of(2026, 10, 20))
                .status(Status.COMPLETED)
                .build();

        // when
        Content content = dto.toEntity();

        // then
        assertThat(content.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("userId 기본값을 넣어도 highlights 기본값(빈 리스트)은 그대로 유지된다")
    void toEntityKeepsDefaultHighlights() {
        // given
        ContentRequestDto dto = ContentRequestDto.builder()
                .title("소년이 온다")
                .viewDate(LocalDate.of(2026, 10, 20))
                .build();

        // when
        Content content = dto.toEntity();

        // then
        assertThat(content.getHighlights()).isNotNull().isEmpty();
    }
}
