package book_log.demo.repository;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface ContentRepository extends JpaRepository<Content, Long> {

    // --- 통계용 프로젝션 인터페이스 (패키지 경로 에러 방지) ---
    public interface CategoryStatInterface {
        String getCategory();
        Long getCount();
    }

    public interface GenreStatInterface {
        String getGenre();
        Long getCount();
    }

    // 저장 여부 판별용 프로젝션: 엔티티 전체(감상평 TEXT 등) 대신 필요한 두 컬럼만 조회
    public interface SavedKeyInterface {
        String getExternalId();
        Category getCategory();
    }

    /**
     * 중복 저장 방지를 위한 조회 메서드 (사용자별: 다른 회원이 같은 작품을 저장했어도 중복이 아니다)
     */
    boolean existsByMemberIdAndExternalIdAndCategory(Long memberId, String externalId, Category category);

    /**
     * 검색 결과 여러 건의 저장 여부를 한 번의 IN 쿼리로 확인 (건별 조회 N+1 방지). 해당 회원의 기록만 본다.
     * externalId만 IN으로 거르고, category 일치 여부는 호출하는 쪽에서 (externalId, category) 쌍으로 판단한다.
     */
    List<SavedKeyInterface> findByMemberIdAndExternalIdIn(Long memberId, Collection<String> externalIds);

    // 기간 조건은 모두 [start, end) 반개구간이다.
    // 컬럼에 함수(YEAR, TO_CHAR)를 씌우면 (member_id, view_date) 인덱스를 못 타므로 범위로 비교한다.

    // 1. 연도+카테고리 복합 필터링
    @Query("SELECT c FROM Content c WHERE c.member.id = :memberId AND c.viewDate >= :start AND c.viewDate < :end AND c.category = :category")
    List<Content> findByYearAndCategory(@Param("memberId") Long memberId, @Param("start") LocalDate start,
                                        @Param("end") LocalDate end, @Param("category") Category category);

    // 2. 연도로만 필터링
    @Query("SELECT c FROM Content c WHERE c.member.id = :memberId AND c.viewDate >= :start AND c.viewDate < :end")
    List<Content> findByYear(@Param("memberId") Long memberId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    // 3. 카테고리로만 필터링
    List<Content> findByMemberIdAndCategory(Long memberId, Category category);

    // 4. 조건 없이 회원의 전체 기록
    List<Content> findByMemberId(Long memberId);

    // 사이드바 미니 캘린더용
    @Query(value = """
        SELECT DISTINCT CAST(EXTRACT(DAY FROM c.view_date) AS INTEGER)
        FROM content c
        WHERE c.member_id = :memberId
          AND c.view_date >= :start
          AND c.view_date < :end
        ORDER BY 1
        """, nativeQuery = true)
    List<Integer> findActiveDays(
        @Param("memberId") Long memberId,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    // --- 통계 API용 쿼리 (인터페이스 방식이라 경로가 필요 없음!) ---

    // 카테고리별 집계
    @Query("SELECT STR(c.category) as category, COUNT(c) as count " +
       "FROM Content c WHERE c.member.id = :memberId AND c.viewDate >= :start AND c.viewDate < :end " +
       "GROUP BY c.category " +
       "ORDER BY COUNT(c) DESC")
    List<CategoryStatInterface> countByCategoryInterface(@Param("memberId") Long memberId,
                                                         @Param("start") LocalDate start, @Param("end") LocalDate end);

    // 장르별 집계
    @Query("SELECT c.genre as genre, COUNT(c) as count " +
           "FROM Content c WHERE c.member.id = :memberId AND c.viewDate >= :start AND c.viewDate < :end " +
           "GROUP BY c.genre ORDER BY COUNT(c) DESC")
    List<GenreStatInterface> countByGenreInterface(@Param("memberId") Long memberId,
                                                   @Param("start") LocalDate start, @Param("end") LocalDate end);

    // 해당 기간 평균 별점
    @Query("SELECT AVG(c.rating) FROM Content c WHERE c.member.id = :memberId AND c.viewDate >= :start AND c.viewDate < :end")
    Double getAverageRating(@Param("memberId") Long memberId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    // 특정 기간의 총 콘텐츠 수
    @Query("SELECT COUNT(c) FROM Content c WHERE c.member.id = :memberId AND c.viewDate >= :start AND c.viewDate < :end")
    long countByMonth(@Param("memberId") Long memberId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    // 가장 활발한 요일 (요일 계산은 SELECT/GROUP BY에 남기고 WHERE만 범위 조건)
    @Query(value = "SELECT EXTRACT(DOW FROM c.view_date) as dow, COUNT(*) as count " +
        "FROM content c " +
        "WHERE c.member_id = :memberId AND c.view_date >= :start AND c.view_date < :end " +
        "GROUP BY dow ORDER BY COUNT(*) DESC LIMIT 1", nativeQuery = true)
    Object[] findMostActiveDay(@Param("memberId") Long memberId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
