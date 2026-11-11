package book_log.demo.repository;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.domain.Member;
import book_log.demo.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 운영 DB와 같은 엔진(PostgreSQL)에서 통계/캘린더 쿼리를 실제로 실행해 본다.
// 컨테이너는 이 클래스 안에서만 뜨고, 스키마는 컨테이너 안에서 create로 만든다. Supabase에는 붙지 않는다.
// Docker가 없으면 클래스 전체가 skip된다(disabledWithoutDocker).
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ContentRepositoryPostgresTest {

    @Configuration
    @EnableJpaAuditing
    @EntityScan(basePackages = "book_log.demo.domain")
    @EnableJpaRepositories(basePackages = "book_log.demo.repository")
    static class TestConfig {
    }

    // 운영 Supabase는 PostgreSQL 17.6(2026-11-11 확인). 메이저 버전만 맞추고 마이너는 고정하지 않는다 (decisions.md 참고)
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member a;
    private Member b;

    private static final LocalDate MAR = LocalDate.of(2026, 3, 1);
    private static final LocalDate APR = LocalDate.of(2026, 4, 1);
    private static final LocalDate MAY = LocalDate.of(2026, 5, 1);
    private static final LocalDate JUN = LocalDate.of(2026, 6, 1);

    @BeforeEach
    void setUp() {
        a = memberRepository.save(Member.builder().username("a").password("x").email("a@example.com").build());
        b = memberRepository.save(Member.builder().username("b").password("x").email("b@example.com").build());
    }

    private void save(Member owner, Category category, LocalDate viewDate, Double rating, String genre) {
        contentRepository.save(Content.builder()
                .member(owner).title("t").category(category).viewDate(viewDate).rating(rating).genre(genre)
                .build());
    }

    private static LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    // 2026-03 (a): 월(2일 2건, 9, 16), 금(6). 월요일이 4건으로 1위. 경계 날짜와 다른 회원 데이터를 섞어 둔다.
    private void seedClearWinner() {
        save(a, Category.BOOK, d(2026, 3, 2), 4.0, "소설");
        save(a, Category.MOVIE, d(2026, 3, 2), 5.0, "SF");
        save(a, Category.BOOK, d(2026, 3, 9), 3.0, "소설");
        save(a, Category.BOOK, d(2026, 3, 16), null, null);
        save(a, Category.MOVIE, d(2026, 3, 6), 4.0, "SF");
        save(a, Category.BOOK, d(2026, 2, 28), 1.0, "에세이");   // 전달 경계
        save(a, Category.BOOK, d(2026, 4, 1), 1.0, "에세이");    // 다음 달 경계
        save(b, Category.MOVIE, d(2026, 3, 20), 1.0, "공포");    // 다른 회원
        contentRepository.flush();
    }

    // 실제 관측: 결과는 평평한 [dow, count]가 아니라 한 행짜리 중첩 배열 [[dow, count]]이다.
    private static Object[] onlyRow(Object[] result) {
        assertThat(result).hasSize(1);
        assertThat(result[0]).isInstanceOf(Object[].class);
        Object[] row = (Object[]) result[0];
        assertThat(row).hasSize(2);
        return row;
    }

    @Test
    @DisplayName("findActiveDays: 해당 월·회원의 날짜만 중복 없이 오름차순, 경계/타 회원 제외")
    void activeDays() {
        seedClearWinner();

        List<Integer> days = contentRepository.findActiveDays(a.getId(), MAR, APR);

        assertThat(days).containsExactly(2, 6, 9, 16);
        assertThat((Object) days.get(0)).isInstanceOf(Integer.class);
    }

    @Test
    @DisplayName("findActiveDays: 기록 없는 달/회원은 빈 리스트")
    void activeDaysEmpty() {
        seedClearWinner();

        assertThat(contentRepository.findActiveDays(a.getId(), MAY, JUN)).isEmpty();
        assertThat(contentRepository.findActiveDays(b.getId(), APR, MAY)).isEmpty();
    }

    @Test
    @DisplayName("findMostActiveDay: 1위가 명확하면 [[dow, count]] 한 행 (2026-03-02는 월요일 = DOW 1)")
    void mostActiveDayClearWinner() {
        seedClearWinner();
        assertThat(d(2026, 3, 2).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);

        Object[] row = onlyRow(contentRepository.findMostActiveDay(a.getId(), MAR, APR));

        System.out.println("[관측] 행 요소 타입 dow=" + row[0].getClass().getName() + ", count=" + row[1].getClass().getName()
                + ", 값=" + java.util.Arrays.toString(row));
        assertThat(((Number) row[0]).intValue()).isEqualTo(1);
        assertThat(((Number) row[1]).longValue()).isEqualTo(4L);
    }

    @Test
    @DisplayName("findMostActiveDay: 동률이면 에러 없이 공동 1위 중 한 행만 온다")
    void mostActiveDayTie() {
        save(a, Category.BOOK, d(2026, 3, 2), null, null);   // 월
        save(a, Category.BOOK, d(2026, 3, 9), null, null);   // 월
        save(a, Category.MOVIE, d(2026, 3, 6), null, null);  // 금
        save(a, Category.MOVIE, d(2026, 3, 13), null, null); // 금
        contentRepository.flush();

        Object[] row = onlyRow(contentRepository.findMostActiveDay(a.getId(), MAR, APR));

        System.out.println("[관측] 동률 결과 dow=" + row[0]);
        assertThat(((Number) row[0]).intValue()).isIn(1, 5); // ORDER BY에 보조 정렬이 없어 어느 쪽이 올지는 DB 마음
        assertThat(((Number) row[1]).longValue()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findMostActiveDay: 기록 없는 달은 null이 아니라 길이 0 배열")
    void mostActiveDayEmpty() {
        seedClearWinner();

        Object[] result = contentRepository.findMostActiveDay(a.getId(), MAY, JUN);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("요일 매핑: PostgreSQL DOW(0=일~6=토)가 자바 요일과 일치한다")
    void dowMatchesJavaDayOfWeek() {
        for (int day = 1; day <= 7; day++) { // 2026-03-01(일) ~ 03-07(토)
            save(a, Category.BOOK, d(2026, 3, day), null, null);
        }
        contentRepository.flush();

        assertThat(d(2026, 3, 1).getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        for (int day = 1; day <= 7; day++) {
            LocalDate date = d(2026, 3, day);
            Object[] row = onlyRow(contentRepository.findMostActiveDay(a.getId(), date, date.plusDays(1)));

            assertThat(((Number) row[0]).intValue()).as(date.toString())
                    .isEqualTo(date.getDayOfWeek().getValue() % 7); // 자바 월=1..일=7 → 일=0
        }
    }

    @Test
    @DisplayName("통계 쿼리: 카테고리/장르 집계는 개수 내림차순, 프로젝션 별칭 매핑이 동작한다")
    void statsAggregations() {
        seedClearWinner();

        var categories = contentRepository.countByCategoryInterface(a.getId(), MAR, APR);
        assertThat(categories).extracting(ContentRepository.CategoryStatInterface::getCategory).containsExactly("BOOK", "MOVIE");
        assertThat(categories).extracting(ContentRepository.CategoryStatInterface::getCount).containsExactly(3L, 2L);

        var genres = contentRepository.countByGenreInterface(a.getId(), MAR, APR);
        assertThat(genres).extracting(ContentRepository.GenreStatInterface::getCount).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(genres).hasSize(3); // 소설 2, SF 2, null 1
        assertThat(genres).filteredOn(g -> "소설".equals(g.getGenre())).extracting(ContentRepository.GenreStatInterface::getCount).containsExactly(2L);
        assertThat(genres).filteredOn(g -> "SF".equals(g.getGenre())).extracting(ContentRepository.GenreStatInterface::getCount).containsExactly(2L);
        assertThat(genres).filteredOn(g -> g.getGenre() == null).extracting(ContentRepository.GenreStatInterface::getCount).containsExactly(1L);
    }

    @Test
    @DisplayName("통계 쿼리: 평균은 null 평점을 빼고 계산하고, 기록 없으면 null, 개수는 반개구간")
    void averageAndCount() {
        seedClearWinner();

        // 3월 a의 평점: 4, 5, 3, null, 4 → (4+5+3+4)/4 = 4.0
        assertThat(contentRepository.getAverageRating(a.getId(), MAR, APR)).isEqualTo(4.0);
        assertThat(contentRepository.getAverageRating(a.getId(), MAY, JUN)).isNull();
        assertThat(contentRepository.countByMonth(a.getId(), MAR, APR)).isEqualTo(5L);
        assertThat(contentRepository.countByMonth(a.getId(), MAY, JUN)).isZero();
        assertThat(contentRepository.countByCategoryInterface(a.getId(), MAY, JUN)).isEmpty();
    }

    @Test
    @DisplayName("StatisticsService: 실제 리포지토리 결과로 mostActiveDay를 요일 문자열로 바꾼다")
    void serviceMostActiveDayWithRealRepository() {
        seedClearWinner();
        StatisticsService service = new StatisticsService(contentRepository);

        assertThat(service.getMonthlyStatistics(a.getId(), 2026, 3).getMostActiveDay()).isEqualTo("월요일");
        assertThat(service.getMonthlyStatistics(a.getId(), 2026, 5).getMostActiveDay()).isEqualTo("기록 없음");
    }
}
