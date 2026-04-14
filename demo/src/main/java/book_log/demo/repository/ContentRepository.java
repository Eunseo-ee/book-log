package book_log.demo.repository;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.dto.response.StatisticsResponse.CategoryStat;
import book_log.demo.dto.response.StatisticsResponse.GenreStat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ContentRepository extends JpaRepository<Content, Long> {
    
    /**
     * 중복 저장 방지를 위한 조회 메서드
     * 동일한 카테고리 내에서 동일한 외부 ID(ISBN, TMDB ID 등)를 가진 데이터가 있는지 확인
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

    // 사이드바 미니 캘린더용 : 특정 월의 기록이 있는 날짜 리스트 조회
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

    // 카테고리별 집계
    @Query("SELECT new com.booklog.dto.StatisticsResponse$CategoryStat(c.category, COUNT(c)) " +
           "FROM Content c WHERE FUNCTION('DATE_FORMAT', c.viewDate, '%Y-%m') = :yearMonth " +
           "GROUP BY c.category")
    List<CategoryStat> countByCategory(String yearMonth);

    // 장르별 집계 (최애 장르 추출용)
    @Query("SELECT new com.booklog.dto.StatisticsResponse$GenreStat(c.genre, COUNT(c)) " +
           "FROM Content c WHERE FUNCTION('DATE_FORMAT', c.viewDate, '%Y-%m') = :yearMonth " +
           "GROUP BY c.genre ORDER BY COUNT(c) DESC")
    List<GenreStat> countByGenre(String yearMonth);

    // 해당 월 평균 별점
    @Query("SELECT AVG(c.rating) FROM Content c WHERE FUNCTION('DATE_FORMAT', c.viewDate, '%Y-%m') = :yearMonth")
    Double getAverageRating(String yearMonth);

    // 특정 월의 총 콘텐츠 수 (전월 대비 비교용)
    @Query("SELECT COUNT(c) FROM Content c WHERE FUNCTION('DATE_FORMAT', c.viewDate, '%Y-%m') = :yearMonth")
    long countByMonth(String yearMonth);
}
