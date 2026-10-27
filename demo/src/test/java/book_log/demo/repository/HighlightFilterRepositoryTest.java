package book_log.demo.repository;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.domain.Highlight;
import book_log.demo.domain.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 기본 Replace.ANY라서 임베디드 H2로 돈다 (실제 DB에 붙지 않음)
@DataJpaTest
class HighlightFilterRepositoryTest {

    // 테스트 폴더의 DemoApplication 대신 이 설정만 쓴다 (ApiConfig 등 슬라이스 밖 빈을 끌어오지 않게)
    @Configuration
    @EnableJpaAuditing
    @EntityScan(basePackages = "book_log.demo.domain")
    @EnableJpaRepositories(basePackages = "book_log.demo.repository")
    static class TestConfig {
    }

    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    @Autowired
    private HighlightRepository highlightRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager em;

    private Member member;

    private Member member() {
        if (member == null) {
            member = memberRepository.save(Member.builder()
                    .username("tester").password("not-a-real-hash").email("tester@example.com").build());
        }
        return member;
    }

    private Content saveContent(String title, Category category, LocalDate viewDate) {
        return contentRepository.save(Content.builder()
                .member(member())
                .title(title)
                .category(category)
                .viewDate(viewDate)
                .build());
    }

    private Highlight saveHighlight(String text, Content content) {
        return highlightRepository.save(Highlight.builder().text(text).content(content).build());
    }

    private List<Highlight> find(Integer year, Collection<Category> categories) {
        Specification<Highlight> spec = Specification
                .where(HighlightSpecifications.viewYear(year))
                .and(HighlightSpecifications.categoryIn(categories));
        return highlightRepository.findAll(spec, LATEST_FIRST);
    }

    @Test
    @DisplayName("조건이 없으면 전체를 최신순(createdAt desc, id desc)으로 반환한다")
    void noFilter() {
        Content book = saveContent("책", Category.BOOK, LocalDate.of(2025, 5, 1));
        Content movie = saveContent("영화", Category.MOVIE, LocalDate.of(2026, 5, 1));
        saveHighlight("첫 번째", book);
        saveHighlight("두 번째", movie);
        saveHighlight("세 번째", book);

        List<Highlight> result = find(null, null);

        assertThat(result).extracting(Highlight::getText)
                .containsExactly("세 번째", "두 번째", "첫 번째");
    }

    @Test
    @DisplayName("연도 필터는 Content.viewDate 기준 [1/1, 다음 해 1/1) 범위다")
    void yearBoundary() {
        Content lastDayOf2025 = saveContent("2025 마지막 날", Category.BOOK, LocalDate.of(2025, 12, 31));
        Content firstDayOf2026 = saveContent("2026 첫날", Category.BOOK, LocalDate.of(2026, 1, 1));
        Content lastDayOf2026 = saveContent("2026 마지막 날", Category.BOOK, LocalDate.of(2026, 12, 31));
        Content firstDayOf2027 = saveContent("2027 첫날", Category.BOOK, LocalDate.of(2027, 1, 1));
        saveHighlight("25-12-31", lastDayOf2025);
        saveHighlight("26-01-01", firstDayOf2026);
        saveHighlight("26-12-31", lastDayOf2026);
        saveHighlight("27-01-01", firstDayOf2027);

        List<Highlight> result = find(2026, null);

        assertThat(result).extracting(Highlight::getText)
                .containsExactly("26-12-31", "26-01-01");
    }

    @Test
    @DisplayName("단일 카테고리로 거른다")
    void singleCategory() {
        saveHighlight("책 문장", saveContent("책", Category.BOOK, LocalDate.of(2026, 3, 1)));
        saveHighlight("영화 대사", saveContent("영화", Category.MOVIE, LocalDate.of(2026, 3, 1)));

        List<Highlight> result = find(null, List.of(Category.MOVIE));

        assertThat(result).extracting(Highlight::getText).containsExactly("영화 대사");
    }

    @Test
    @DisplayName("애니 묶음(ANIME, ANIME_MOVIE, ANIME_TVA)을 한 번에 거른다")
    void animeGroup() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        saveHighlight("애니", saveContent("애니", Category.ANIME, date));
        saveHighlight("극장판", saveContent("극장판", Category.ANIME_MOVIE, date));
        saveHighlight("TVA", saveContent("TVA", Category.ANIME_TVA, date));
        saveHighlight("드라마", saveContent("드라마", Category.TV, date));

        List<Highlight> result = find(null, List.of(Category.ANIME, Category.ANIME_MOVIE, Category.ANIME_TVA));

        assertThat(result).extracting(Highlight::getText)
                .containsExactly("TVA", "극장판", "애니");
    }

    @Test
    @DisplayName("연도와 카테고리는 AND로 묶인다")
    void yearAndCategory() {
        saveHighlight("2026 책", saveContent("2026 책", Category.BOOK, LocalDate.of(2026, 6, 1)));
        saveHighlight("2025 책", saveContent("2025 책", Category.BOOK, LocalDate.of(2025, 6, 1)));
        saveHighlight("2026 영화", saveContent("2026 영화", Category.MOVIE, LocalDate.of(2026, 6, 1)));

        List<Highlight> result = find(2026, List.of(Category.BOOK));

        assertThat(result).extracting(Highlight::getText).containsExactly("2026 책");
    }

    @Test
    @DisplayName("조건에 맞는 하이라이트가 없으면 빈 리스트를 반환한다")
    void emptyResult() {
        saveHighlight("책 문장", saveContent("책", Category.BOOK, LocalDate.of(2026, 3, 1)));

        assertThat(find(2020, null)).isEmpty();
        assertThat(find(null, List.of(Category.TV))).isEmpty();
    }

    @Test
    @DisplayName("EntityGraph로 content가 함께 로딩된다")
    void contentLoadedWithEntityGraph() {
        saveHighlight("문장 1", saveContent("책 1", Category.BOOK, LocalDate.of(2026, 3, 1)));
        saveHighlight("문장 2", saveContent("책 2", Category.BOOK, LocalDate.of(2026, 3, 2)));
        // 영속성 컨텍스트를 비워서 저장 때 들고 있던 Content 객체가 아니라 DB 조회 결과를 보게 한다
        em.flush();
        em.clear();

        List<Highlight> result = find(null, null);

        PersistenceUnitUtil util = em.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(h -> assertThat(util.isLoaded(h, "content")).isTrue());
    }
}
