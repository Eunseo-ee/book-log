package book_log.demo.repository;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.domain.Highlight;
import book_log.demo.domain.Member;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 회원 A, B의 데이터가 조회 쿼리에서 섞이지 않는지 리포지토리 레벨에서 검증한다.
// 기본 Replace.ANY라서 임베디드 H2로 돌고(실제 DB에 붙지 않음), @DataJpaTest가 테스트마다 트랜잭션을 롤백한다.
@DataJpaTest
class MemberIsolationRepositoryTest {

    @Configuration
    @EnableJpaAuditing
    @EntityScan(basePackages = "book_log.demo.domain")
    @EnableJpaRepositories(basePackages = "book_log.demo.repository")
    static class TestConfig {
    }

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private HighlightRepository highlightRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member a;
    private Member b;

    @BeforeEach
    void setUp() {
        a = memberRepository.save(Member.builder().username("a").password("x").email("a@example.com").build());
        b = memberRepository.save(Member.builder().username("b").password("x").email("b@example.com").build());
    }

    private Content save(Member owner, String externalId, Category category, LocalDate viewDate, Double rating, String genre) {
        return contentRepository.save(Content.builder()
                .member(owner).externalId(externalId).title(externalId + "-" + owner.getUsername())
                .category(category).viewDate(viewDate).rating(rating).genre(genre)
                .build());
    }

    private Content save(Member owner, Category category, LocalDate viewDate) {
        return save(owner, null, category, viewDate, null, null);
    }

    private static LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    @Test
    @DisplayName("builder로 만든 Content도 member가 지정되어 있어야 저장된다 (userId 기본값 1L 문제는 task 16에서 제거됨)")
    void memberIsRequired() {
        Content saved = save(a, Category.BOOK, d(2026, 3, 1));

        assertThat(saved.getMember().getId()).isEqualTo(a.getId());
    }

    @Test
    @DisplayName("필터 조회 4가지 분기가 모두 요청한 회원의 기록만 반환한다")
    void filterBranchesAreScopedToMember() {
        save(a, "a1", Category.BOOK, d(2026, 3, 1), null, null);
        save(a, "a2", Category.MOVIE, d(2026, 4, 1), null, null);
        save(a, "a3", Category.BOOK, d(2025, 4, 1), null, null);
        save(b, "b1", Category.BOOK, d(2026, 3, 2), null, null);
        save(b, "b2", Category.MOVIE, d(2025, 4, 2), null, null);

        LocalDate start = d(2026, 1, 1);
        LocalDate end = d(2027, 1, 1);

        assertThat(contentRepository.findByYearAndCategory(a.getId(), start, end, Category.BOOK))
                .extracting(Content::getExternalId).containsExactly("a1");
        assertThat(contentRepository.findByYear(a.getId(), start, end))
                .extracting(Content::getExternalId).containsExactlyInAnyOrder("a1", "a2");
        assertThat(contentRepository.findByMemberIdAndCategory(a.getId(), Category.BOOK))
                .extracting(Content::getExternalId).containsExactlyInAnyOrder("a1", "a3");
        assertThat(contentRepository.findByMemberId(a.getId()))
                .extracting(Content::getExternalId).containsExactlyInAnyOrder("a1", "a2", "a3");

        assertThat(contentRepository.findByYearAndCategory(b.getId(), start, end, Category.BOOK))
                .extracting(Content::getExternalId).containsExactly("b1");
        assertThat(contentRepository.findByMemberId(b.getId()))
                .extracting(Content::getExternalId).containsExactlyInAnyOrder("b1", "b2");
    }

    @Test
    @DisplayName("같은 externalId+category를 A, B가 각각 저장할 수 있고 저장 여부는 회원 기준으로만 true다")
    void sameWorkSavedByTwoMembers() {
        save(a, "550", Category.MOVIE, d(2026, 3, 1), null, null);
        // 유니크 제약이 (member_id, external_id, category)라 B의 같은 작품은 위반이 아니다
        save(b, "550", Category.MOVIE, d(2026, 3, 2), null, null);
        save(a, "777", Category.MOVIE, d(2026, 3, 3), null, null);
        contentRepository.flush();

        assertThat(contentRepository.existsByMemberIdAndExternalIdAndCategory(a.getId(), "550", Category.MOVIE)).isTrue();
        assertThat(contentRepository.existsByMemberIdAndExternalIdAndCategory(b.getId(), "550", Category.MOVIE)).isTrue();
        // B는 777을 저장한 적이 없다
        assertThat(contentRepository.existsByMemberIdAndExternalIdAndCategory(b.getId(), "777", Category.MOVIE)).isFalse();
        // 같은 externalId라도 카테고리가 다르면 다른 작품이다
        assertThat(contentRepository.existsByMemberIdAndExternalIdAndCategory(a.getId(), "550", Category.TV)).isFalse();
    }

    @Test
    @DisplayName("검색 결과 isSaved용 IN 쿼리는 요청한 회원의 저장 건만 반환한다")
    void savedKeysAreScopedToMember() {
        save(a, "550", Category.MOVIE, d(2026, 3, 1), null, null);
        save(b, "999", Category.MOVIE, d(2026, 3, 1), null, null);

        List<ContentRepository.SavedKeyInterface> forA =
                contentRepository.findByMemberIdAndExternalIdIn(a.getId(), List.of("550", "999"));
        List<ContentRepository.SavedKeyInterface> forB =
                contentRepository.findByMemberIdAndExternalIdIn(b.getId(), List.of("550", "999"));

        assertThat(forA).extracting(ContentRepository.SavedKeyInterface::getExternalId).containsExactly("550");
        assertThat(forB).extracting(ContentRepository.SavedKeyInterface::getExternalId).containsExactly("999");
    }

    @Test
    @DisplayName("월별 통계 쿼리 5개가 회원별로 분리된다")
    void monthlyStatisticsAreScopedToMember() {
        LocalDate start = d(2026, 5, 1);
        LocalDate end = d(2026, 6, 1);
        // 2026-05-04(월), 05(화) / A: 영화 2 + 책 1, B: 책 1
        save(a, "a1", Category.MOVIE, d(2026, 5, 4), 5.0, "액션");
        save(a, "a2", Category.MOVIE, d(2026, 5, 4), 3.0, "액션");
        save(a, "a3", Category.BOOK, d(2026, 5, 5), 4.0, "소설");
        save(b, "b1", Category.BOOK, d(2026, 5, 5), 1.0, "시");

        assertThat(contentRepository.countByMonth(a.getId(), start, end)).isEqualTo(3);
        assertThat(contentRepository.countByMonth(b.getId(), start, end)).isEqualTo(1);

        assertThat(contentRepository.getAverageRating(a.getId(), start, end)).isEqualTo(4.0);
        assertThat(contentRepository.getAverageRating(b.getId(), start, end)).isEqualTo(1.0);

        List<ContentRepository.CategoryStatInterface> categoriesA =
                contentRepository.countByCategoryInterface(a.getId(), start, end);
        assertThat(categoriesA).extracting(ContentRepository.CategoryStatInterface::getCategory)
                .containsExactly("MOVIE", "BOOK");
        assertThat(categoriesA).extracting(ContentRepository.CategoryStatInterface::getCount).containsExactly(2L, 1L);
        assertThat(contentRepository.countByCategoryInterface(b.getId(), start, end))
                .extracting(ContentRepository.CategoryStatInterface::getCategory).containsExactly("BOOK");

        assertThat(contentRepository.countByGenreInterface(a.getId(), start, end))
                .extracting(ContentRepository.GenreStatInterface::getGenre).containsExactly("액션", "소설");
        assertThat(contentRepository.countByGenreInterface(b.getId(), start, end))
                .extracting(ContentRepository.GenreStatInterface::getGenre).containsExactly("시");

        // 가장 활발한 요일: A는 5/4(월) 2건이 1위, B는 5/5(화) 1건. 요일 숫자는 DB마다 다르다(PostgreSQL 일=0, H2 일=1)
        // 그래서 숫자 대신 건수와 회원별 분리만 검증한다. 요일 매핑(0=일요일)은 PostgreSQL에서 확인 필요
        Object[] topA = row(contentRepository.findMostActiveDay(a.getId(), start, end));
        Object[] topB = row(contentRepository.findMostActiveDay(b.getId(), start, end));
        assertThat(((Number) topA[1]).intValue()).isEqualTo(2);
        assertThat(((Number) topB[1]).intValue()).isEqualTo(1);
        assertThat(((Number) topA[0]).intValue()).isNotEqualTo(((Number) topB[0]).intValue());
    }

    // 네이티브 쿼리 결과는 [dow, count] 한 행이거나 [[dow, count]] 형태로 온다 (StatisticsService와 같은 처리)
    private static Object[] row(Object[] result) {
        return (result.length > 0 && result[0] instanceof Object[] inner) ? inner : result;
    }

    @Test
    @DisplayName("기록이 없는 회원의 통계는 0/null이다")
    void emptyMember() {
        save(a, "a1", Category.MOVIE, d(2026, 5, 4), 5.0, "액션");

        assertThat(contentRepository.countByMonth(b.getId(), d(2026, 5, 1), d(2026, 6, 1))).isZero();
        assertThat(contentRepository.getAverageRating(b.getId(), d(2026, 5, 1), d(2026, 6, 1))).isNull();
        assertThat(contentRepository.findActiveDays(b.getId(), d(2026, 5, 1), d(2026, 6, 1))).isEmpty();
    }

    @Test
    @DisplayName("캘린더 활동일 쿼리가 회원별로 분리된다")
    void activeDaysAreScopedToMember() {
        save(a, Category.BOOK, d(2026, 5, 3));
        save(a, Category.BOOK, d(2026, 5, 3)); // 같은 날 두 건은 하루로 센다
        save(a, Category.BOOK, d(2026, 5, 20));
        save(b, Category.BOOK, d(2026, 5, 9));

        assertThat(contentRepository.findActiveDays(a.getId(), d(2026, 5, 1), d(2026, 6, 1))).containsExactly(3, 20);
        assertThat(contentRepository.findActiveDays(b.getId(), d(2026, 5, 1), d(2026, 6, 1))).containsExactly(9);
    }

    @Test
    @DisplayName("월 경계: 1/31은 1월, 2/1은 2월 범위에 들어간다 (반개구간)")
    void monthBoundary() {
        save(a, "jan31", Category.BOOK, d(2026, 1, 31), 2.0, null);
        save(a, "feb01", Category.BOOK, d(2026, 2, 1), 4.0, null);

        assertThat(contentRepository.countByMonth(a.getId(), d(2026, 1, 1), d(2026, 2, 1))).isEqualTo(1);
        assertThat(contentRepository.countByMonth(a.getId(), d(2026, 2, 1), d(2026, 3, 1))).isEqualTo(1);
        assertThat(contentRepository.getAverageRating(a.getId(), d(2026, 1, 1), d(2026, 2, 1))).isEqualTo(2.0);
        assertThat(contentRepository.getAverageRating(a.getId(), d(2026, 2, 1), d(2026, 3, 1))).isEqualTo(4.0);
        assertThat(contentRepository.findActiveDays(a.getId(), d(2026, 1, 1), d(2026, 2, 1))).containsExactly(31);
        assertThat(contentRepository.findActiveDays(a.getId(), d(2026, 2, 1), d(2026, 3, 1))).containsExactly(1);
    }

    @Test
    @DisplayName("연 경계: 12/31은 그 해, 다음 해 1/1은 다음 해 범위에 들어간다")
    void yearBoundary() {
        save(a, "dec31", Category.BOOK, d(2025, 12, 31), null, null);
        save(a, "jan01", Category.BOOK, d(2026, 1, 1), null, null);

        assertThat(contentRepository.findByYear(a.getId(), d(2025, 1, 1), d(2026, 1, 1)))
                .extracting(Content::getExternalId).containsExactly("dec31");
        assertThat(contentRepository.findByYear(a.getId(), d(2026, 1, 1), d(2027, 1, 1)))
                .extracting(Content::getExternalId).containsExactly("jan01");
        // 통계 쿼리의 12월 범위는 다음 해 1/1을 포함하지 않는다
        assertThat(contentRepository.countByMonth(a.getId(), d(2025, 12, 1), d(2026, 1, 1))).isEqualTo(1);
        assertThat(contentRepository.countByMonth(a.getId(), d(2026, 1, 1), d(2026, 2, 1))).isEqualTo(1);
    }

    @Test
    @DisplayName("하이라이트 모음집이 회원별로 분리된다 (연도/카테고리 조건과 AND)")
    void highlightsAreScopedToMember() {
        Content bookA = save(a, Category.BOOK, d(2026, 3, 1));
        Content movieA = save(a, Category.MOVIE, d(2026, 3, 1));
        Content bookB = save(b, Category.BOOK, d(2026, 3, 1));
        highlightRepository.save(Highlight.builder().text("A 책").content(bookA).build());
        highlightRepository.save(Highlight.builder().text("A 영화").content(movieA).build());
        highlightRepository.save(Highlight.builder().text("B 책").content(bookB).build());
        Sort sort = Sort.by(Sort.Order.asc("id"));

        List<Highlight> forA = highlightRepository.findAll(Specification.where(HighlightSpecifications.ownedBy(a.getId())), sort);
        List<Highlight> forB = highlightRepository.findAll(Specification.where(HighlightSpecifications.ownedBy(b.getId())), sort);
        List<Highlight> bookOfA = highlightRepository.findAll(Specification
                .where(HighlightSpecifications.ownedBy(a.getId()))
                .and(HighlightSpecifications.viewYear(2026))
                .and(HighlightSpecifications.categoryIn(List.of(Category.BOOK))), sort);

        assertThat(forA).extracting(Highlight::getText).containsExactly("A 책", "A 영화");
        assertThat(forB).extracting(Highlight::getText).containsExactly("B 책");
        assertThat(bookOfA).extracting(Highlight::getText).containsExactly("A 책");
    }

    @Test
    @DisplayName("ownedBy(null)은 '조건 없음'이 아니라 예외다 (전체 회원 데이터 노출 방지)")
    void ownedByRejectsNull() {
        assertThatThrownBy(() -> HighlightSpecifications.ownedBy(null)).isInstanceOf(NullPointerException.class);
    }
}
