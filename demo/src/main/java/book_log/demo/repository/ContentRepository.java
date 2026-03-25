package book_log.demo.repository;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContentRepository extends JpaRepository<Content, Long> {
    
    /**
     * 중복 저장 방지를 위한 조회 메서드
     * 동일한 카테고리 내에서 동일한 외부 ID(ISBN, TMDB ID 등)를 가진 데이터가 있는지 확인
     */
    Optional<Content> findByExternalIdAndCategory(String externalId, Category category);

}
