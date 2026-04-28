package book_log.demo.repository;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
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

    /**
     * 중복 저장 방지를 위한 조회 메서드
     */
    Optional<Content> findByExternalIdAndCategory(String externalId, Category category);

    // 1. 연도+카테고리 복합 필터링
    @Query("SELECT c FROM Content c WHERE YEAR(c.viewDate) = :year AND c.category = :category")
    List<Content> findByYearAndCategory(@Param("year") int year, @Param("category") Category category);

    // 2. 연도로만 필터링
    @Query("SELECT c FROM Content c WHERE YEAR(c.viewDate) = :year")
    List<Content> findByYear(@Param("year") int year);

    // 3. 카테고리로만 필터링
    List<Content> findByCategory(Category category);

    // 사이드바 미니 캘린더용
    @Query(value = """ 
        SELECT DISTINCT CAST(EXTRACT(DAY FROM c.view_date) AS INTEGER)
        FROM content c 
        WHERE c.user_id = :userId 
          AND EXTRACT(YEAR FROM c.view_date) = :year 
          AND EXTRACT(MONTH FROM c.view_date) = :month
        ORDER BY 1
        """, nativeQuery = true)
    List<Integer> findActiveDays(
        @Param("userId") Long userId,
        @Param("year") int year,
        @Param("month") int month
    );

    // --- 통계 API용 쿼리 (인터페이스 방식이라 경로가 필요 없음!) ---

    // 카테고리별 집계
    @Query("SELECT c.category as category, COUNT(c) as count " +
           "FROM Content c WHERE TO_CHAR(c.viewDate, 'YYYY-MM') = :yearMonth " +
           "GROUP BY c.category")
    List<CategoryStatInterface> countByCategoryInterface(@Param("yearMonth") String yearMonth);

    // 장르별 집계
    @Query("SELECT c.genre as genre, COUNT(c) as count " +
           "FROM Content c WHERE TO_CHAR(c.viewDate, 'YYYY-MM') = :yearMonth " +
           "GROUP BY c.genre ORDER BY COUNT(c) DESC")
    List<GenreStatInterface> countByGenreInterface(@Param("yearMonth") String yearMonth);

    // 해당 월 평균 별점
    @Query("SELECT AVG(c.rating) FROM Content c WHERE TO_CHAR(c.viewDate, 'YYYY-MM') = :yearMonth")
    Double getAverageRating(@Param("yearMonth") String yearMonth);

    // 특정 월의 총 콘텐츠 수
    @Query("SELECT COUNT(c) FROM Content c WHERE TO_CHAR(c.viewDate, 'YYYY-MM') = :yearMonth")
    long countByMonth(@Param("yearMonth") String yearMonth);

    // 가장 활발한 요일
    @Query(value = "SELECT EXTRACT(DOW FROM c.view_date) as dow, COUNT(*) as count " +
        "FROM content c " +
        "WHERE TO_CHAR(c.view_date, 'YYYY-MM') = :yearMonth " +
        "GROUP BY dow ORDER BY COUNT(*) DESC LIMIT 1", nativeQuery = true)
    Object[] findMostActiveDay(@Param("yearMonth") String yearMonth);
}