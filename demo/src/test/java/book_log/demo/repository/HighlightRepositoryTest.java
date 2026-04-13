package book_log.demo.repository;

// JUnit 5 (테스트 프레임워크)
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// AssertJ (검증 라이브러리)
import static org.assertj.core.api.Assertions.assertThat;

// Spring Boot Test (JPA 테스트용)
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import book_log.demo.DemoApplication;
// 엔티티들 (패키지 경로 확인 필요!)
import book_log.demo.domain.Content;
import book_log.demo.domain.Highlight;

import java.util.List;
// Java 기본 유틸
import java.util.Optional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// DemoApplication 대신, 테스트에 꼭 필요한 '최소 단위'만 명시합니다.
@ContextConfiguration(classes = {
    HighlightRepository.class,
    ContentRepository.class
})
// 엔티티 패키지 경로를 직접 짚어줍니다.
@EntityScan(basePackages = "book_log.demo.domain")
// 레포지토리 패키지 경로를 직접 짚어줍니다.
@EnableJpaRepositories(basePackages = "book_log.demo.repository")
class HighlightRepositoryTest {

    @Autowired
    private HighlightRepository highlightRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Test
    @DisplayName("하이라이트 저장 및 조회 테스트")
    void saveAndFindHighlight() {
        // 1. Given: 테스트 데이터 준비 (연관된 Content가 먼저 있어야 함)
        Content content = Content.builder()
                .title("테스트 책")
                .authorOrDirector("은서")
                .build();
        contentRepository.save(content);

        Highlight highlight = Highlight.builder()
                .text("오늘의 한 문장")
                .page(123)
                .content(content)
                .build();

        // 2. When: 저장
        Highlight savedHighlight = highlightRepository.save(highlight);

        // 3. Then: 검증
        Highlight foundHighlight = highlightRepository.findById(savedHighlight.getId()).orElseThrow();
        assertThat(foundHighlight.getText()).isEqualTo("오늘의 한 문장");
        assertThat(foundHighlight.getContent().getTitle()).isEqualTo("테스트 책");
    }

    @Test
    @DisplayName("하이라이트 내용이 null이면 저장에 실패해야 한다")
    void saveHighlightWithNullTest() {
        // Given
        Highlight highlight = Highlight.builder()
                .text(null) // 필수값인 text를 null로 설정
                .build();

        // when & then
        // DB 제약 조건 위반 예외가 발생하는지 검증
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {highlightRepository.saveAndFlush(highlight);
            // saveAndFlush를 써야 즉시 DB에 반영되어 예외가 발생
        });
    }

    @Test
    @DisplayName("특정 컨텐츠의 하이라이트 목록만 조회한다")
    void findByContentId() {
        // given
        Content content1 = contentRepository.save(Content.builder().title("책 1").build());
        Content content2 = contentRepository.save(Content.builder().title("책 2").build());

        highlightRepository.save(Highlight.builder().text("문장 1").content(content1).build());
        highlightRepository.save(Highlight.builder().text("문장 2").content(content1).build());
        highlightRepository.save(Highlight.builder().text("문장 3").content(content2).build());

        // when
        List<Highlight> result = highlightRepository.findByContentId(content1.getId());

        // then
        assertThat(result).hasSize(2); // content1에 속한 2개만 나와야 함
        assertThat(result).extracting("text").containsExactlyInAnyOrder("문장 1", "문장 2");
    }

    @Test
    @DisplayName("하이라이트 내용을 수정하면 DB에 반영되어야 한다")
    void updateHighlight() {
        // given
        Highlight highlight = highlightRepository.save(Highlight.builder().text("원래 내용").build());

        // when
        highlight.update("수정된 내용", 10, 1, 1, "00:10:00");
        highlightRepository.saveAndFlush(highlight); // 변경 사항 강제 반영

        // then
        Highlight updated = highlightRepository.findById(highlight.getId()).orElseThrow();
        assertThat(updated.getText()).isEqualTo("수정된 내용");
    }

    @Test
    @DisplayName("컨텐츠를 삭제하면 연관된 하이라이트 처리 확인")
    void deleteContentAndCheckHighlight() {
        // given
        Content content = contentRepository.save(Content.builder().title("지워질 책").build());
        Highlight highlight = Highlight.builder()
            .text("함께 지워질까?")
            .content(content) // 여기서 content는 이제 'Saved' 상태입니다.
            .build();

        highlightRepository.save(highlight);

        // when
        contentRepository.delete(content);
        contentRepository.flush();

        // then
        // CascadeType.REMOVE 설정이 되어있다면 하이라이트도 0개가 되어야 함
        List<Highlight> result = highlightRepository.findByContentId(content.getId());
        assertThat(result).isEmpty();
    }
}
