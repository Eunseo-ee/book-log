package book_log.demo.repository;

import book_log.demo.domain.Highlight;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HighlightRepository extends JpaRepository<Highlight, Long> {
    // 특정 콘텐츠에 달린 하이라이트들만 뽑아오는 메서드
    List<Highlight> findByContentId(Long contentId);
}
