package book_log.demo.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.domain.Member;
import book_log.demo.domain.Status;

/**
 * 저장 경로(toEntity → Content.builder())에서 member가 null로 빠지지 않는지 서버/DB 없이 확인한다.
 */
class ContentRequestDtoTest {

    @Test
    @DisplayName("toEntity로 만든 Content에는 넘겨준 member가 들어간다")
    void toEntitySetsMember() {
        // given
        ContentRequestDto dto = ContentRequestDto.builder()
                .title("소년이 온다")
                .externalId("9788936434120")
                .category(Category.BOOK)
                .viewDate(LocalDate.of(2026, 10, 20))
                .status(Status.COMPLETED)
                .build();

        // when
        Member member = Member.builder().id(7L).username("u").password("p").email("e@example.com").build();
        Content content = dto.toEntity(member);

        // then
        assertThat(content.getMember()).isSameAs(member);
    }

    @Test
    @DisplayName("member를 넣어도 highlights 기본값(빈 리스트)은 그대로 유지된다")
    void toEntityKeepsDefaultHighlights() {
        // given
        ContentRequestDto dto = ContentRequestDto.builder()
                .title("소년이 온다")
                .viewDate(LocalDate.of(2026, 10, 20))
                .build();

        // when
        Content content = dto.toEntity(Member.builder().id(7L).build());

        // then
        assertThat(content.getHighlights()).isNotNull().isEmpty();
    }
}
