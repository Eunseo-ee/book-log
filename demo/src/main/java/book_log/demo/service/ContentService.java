package book_log.demo.service;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.dto.response.ContentResponseDto;
import book_log.demo.repository.ContentRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)// 최적화
@RequiredArgsConstructor
public class ContentService {
    
    private final ContentRepository contentRepository;

    // UI에서 '저장됨' 표시를 하기 위해 저장 여부만 확인하는 메서드
    public boolean isAlreadySaved(String externalId, Category category) {
        return contentRepository.findByExternalIdAndCategory(externalId, category).isPresent();
    }

    // 콘텐츠 저장 (중복 체크 및 유효성 검사 포함)
    @Transactional
    public Long saveContent(Content content) {
        // 1. 중복 저장 확인 로직
        validateDuplicateContent(content);

        // 2. 평점 유효성 검사(0~5 사이인지)
        validateRating(content.getRating());

        // 3. 저장 후 생성된 ID 반환
        return contentRepository.save(content).getId();
    }

    private void validateDuplicateContent(Content content) {
        if (isAlreadySaved(content.getExternalId(), content.getCategory())) {
            throw new IllegalStateException("이미 등록된 콘텐츠입니다.");
        }
    }
    
    private void validateRating(Double rating) {
        if (rating != null && (rating < 0 || rating > 5)) {
            throw new IllegalArgumentException("평점은 0점과 5점의 사이여야 합니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<ContentResponseDto> getFilteredContents(Integer year, Category category) {
        
        List<Content> contents;

        if (year != null && category != null) {
            // 둘 다 있을 때
            contents = contentRepository.findByYearAndCategory(year, category);
        } else if (year != null) {
            // 연도만 있을 때
            contents = contentRepository.findByYear(year);
        } else if (category != null) {
            // 카테고리만 있을 때
            contents = contentRepository.findByCategory(category);
        } else {
            // 아무 조건 없을 때(전체 조회)
            contents = contentRepository.findAll();
        }

        return contents.stream()
                .map(ContentResponseDto::new)
                .collect(Collectors.toList());

    }

}
