package book_log.demo.repository;

import book_log.demo.domain.Category;
import book_log.demo.domain.Highlight;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;

// 하이라이트 목록의 선택 조건. 조건 값이 없으면 null을 반환해 and()에서 빠지게 한다.
public final class HighlightSpecifications {

    private HighlightSpecifications() {
    }

    // 부모 Content의 소유자가 memberId인 하이라이트. 필수 조건이라 null이면 예외를 던진다
    // (null을 "조건 없음"으로 처리하면 전체 회원의 데이터가 노출된다)
    public static Specification<Highlight> ownedBy(Long memberId) {
        Objects.requireNonNull(memberId, "memberId");
        return (root, query, cb) -> cb.equal(root.get("content").get("member").get("id"), memberId);
    }

    // 부모 Content.viewDate가 [year-01-01, year+1-01-01) 범위인 하이라이트
    public static Specification<Highlight> viewYear(Integer year) {
        if (year == null) {
            return null;
        }
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = start.plusYears(1);
        return (root, query, cb) -> {
            var viewDate = root.get("content").<LocalDate>get("viewDate");
            return cb.and(
                    cb.greaterThanOrEqualTo(viewDate, start),
                    cb.lessThan(viewDate, end));
        };
    }

    // 부모 Content.category가 주어진 카테고리 중 하나인 하이라이트
    public static Specification<Highlight> categoryIn(Collection<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("content").get("category").in(categories);
    }
}
