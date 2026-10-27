package book_log.demo.service;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.dto.request.ContentRequestDto;
import book_log.demo.dto.response.ContentResponseDto;
import book_log.demo.repository.ContentRepository;
import book_log.demo.repository.MemberRepository;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)// 최적화
@RequiredArgsConstructor
public class ContentService {
    
    private final ContentRepository contentRepository;
    private final MemberRepository memberRepository;

    // PostgreSQL/H2 공통: unique_violation (FK 위반은 23503, NOT NULL 위반은 23502)
    private static final String SQL_STATE_UNIQUE_VIOLATION = "23505";

    // 저장된 콘텐츠를 식별하는 키 (externalId만으로는 TMDB 영화/드라마 id가 겹칠 수 있어 category와 함께 사용)
    public record SavedKey(String externalId, Category category) {}

    // UI에서 '저장됨' 표시를 하기 위해 저장 여부만 확인하는 메서드
    public boolean isAlreadySaved(Long memberId, String externalId, Category category) {
        return contentRepository.existsByMemberIdAndExternalIdAndCategory(memberId, externalId, category);
    }

    // 검색 결과 여러 건의 '저장됨' 표시용: 주어진 externalId 중 DB에 있는 (externalId, category) 쌍을 한 번에 반환
    public Set<SavedKey> findSavedKeys(Collection<String> externalIds) {
        if (externalIds == null || externalIds.isEmpty()) {
            return Set.of(); // 빈 IN 절 쿼리 방지
        }
        return contentRepository.findByExternalIdIn(externalIds).stream()
                .map(k -> new SavedKey(k.getExternalId(), k.getCategory()))
                .collect(Collectors.toSet());
    }

    // 콘텐츠 저장 (중복 체크 및 유효성 검사 포함)
    @Transactional
    public Long saveContent(Long memberId, ContentRequestDto requestDto) {
        // 1. 평점 검증 (선택 입력: null이면 '평가 안 함'으로 보고 범위 검증을 건너뜀)
        Double rating = requestDto.getRating();
        if (rating != null && (rating < 0 || rating > 5.0)) {
            throw new IllegalArgumentException("평점은 0에서 5 사이여야 합니다.");
        }

        // 2. 미래 날짜 검증 (추가!)
        if (requestDto.getViewDate() != null && requestDto.getViewDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("미래 날짜의 기록은 저장할 수 없습니다.");
        }

        // 3. 중복 체크
        if (isAlreadySaved(memberId, requestDto.getExternalId(), requestDto.getCategory())) {
            throw new IllegalStateException("이미 저장된 콘텐츠입니다.");
        }

        // DTO를 엔티티로 변환 (member는 SELECT 없이 id만 가진 프록시. 없는 회원이면 FK 위반으로 막힌다)
        Content content = requestDto.toEntity(memberRepository.getReferenceById(memberId));

        // 4. 저장 후 생성된 ID 반환
        try {
            // IDENTITY 전략이라 save() 안에서 INSERT가 바로 나가므로, 유니크 위반도 이 try 안에서 잡힌다
            return contentRepository.save(content).getId();
        } catch (DataIntegrityViolationException e) {
            // 중복 체크와 INSERT 사이에 같은 요청이 먼저 저장된 경우(동시 요청)만 중복으로 본다.
            // FK 위반(없는 회원) 등 다른 무결성 오류는 중복이 아니므로 그대로 던진다
            if (isUniqueViolation(e)) {
                throw new IllegalStateException("이미 저장된 콘텐츠입니다.");
            }
            throw e;
        }
    }

    private boolean isUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && SQL_STATE_UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
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
