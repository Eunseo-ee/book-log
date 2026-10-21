package book_log.demo.repository;

import book_log.demo.domain.Highlight;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

public interface HighlightRepository extends JpaRepository<Highlight, Long>, JpaSpecificationExecutor<Highlight> {
    // 특정 콘텐츠에 달린 하이라이트들만 뽑아오는 메서드
    List<Highlight> findByContentId(Long contentId);

    // 모음집 목록 조회: content를 함께 가져와 하이라이트마다 content를 따로 조회하지 않게 함
    @Override
    @EntityGraph(attributePaths = "content")
    List<Highlight> findAll(Specification<Highlight> spec, Sort sort);
}
