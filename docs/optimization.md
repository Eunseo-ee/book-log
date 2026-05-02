# 성능 최적화 방안

> 현재 코드베이스를 기준으로 실제로 문제가 될 수 있는 지점을 분석.
> 지금 당장 고쳐야 하는 것과 데이터가 쌓인 뒤 고려할 것을 구분함.

---

## 1. N+1 쿼리 발생 가능 지점

### 무엇이 N+1 문제인가?
Content 1건을 조회(1번 쿼리)한 뒤 그 안에 있는 Highlight 목록을 각각 조회(N번 쿼리)하면
총 N+1번의 쿼리가 발생하는 문제.
데이터가 100건이면 101번, 10,000건이면 10,001번 쿼리 실행.

---

### 지점 1: ContentService.getFilteredContents() — 잠재적 위험

**현재 코드:**
```java
// ContentService.java
public List<ContentResponseDto> getFilteredContents(Integer year, Category category) {
    List<Content> contents = contentRepository.findAll(); // 전체 조회

    return contents.stream()
            .map(ContentResponseDto::new)  // ← 여기서 highlights를 접근하면 N+1
            .collect(Collectors.toList());
}
```

**현재 상태**: `ContentResponseDto` 생성자가 `highlights`를 접근하지 않으므로 현재는 안전.

**위험 시나리오**: 나중에 응답에 `highlightCount` 또는 `highlights` 목록을 추가하면 즉시 N+1 발생.
```java
// 이런 코드가 추가되면 즉시 N+1 발생
public ContentResponseDto(Content entity) {
    this.highlightCount = entity.getHighlights().size(); // ← LAZY 로딩 N번 실행
}
```

**해결 방법 (미리 대비):**
```java
// ContentRepository에 Fetch Join 쿼리 추가
@Query("SELECT DISTINCT c FROM Content c LEFT JOIN FETCH c.highlights WHERE ...")
List<Content> findAllWithHighlights();
```

---

### 지점 2: HighlightService.saveHighlight() — Content 별도 조회

**현재 코드:**
```java
// HighlightService.java
public Long saveHighlight(HighlightRequestDto requestDto) {
    Content content = contentRepository.findById(requestDto.getContentId())
            .orElseThrow(...);  // Content 조회 1번
    
    Highlight highlight = Highlight.builder()
            .content(content)
            ...
            .build();
    
    highlightRepository.save(highlight);  // Highlight 저장 1번
}
```
저장 시 Content를 한 번 더 조회하는 구조. 건수가 적어 현재는 문제없지만
배치 저장 시에는 N번 조회가 발생할 수 있음.

**해결 방법 (대규모 배치 시):**
```java
// 참조만 필요할 경우 실제 조회 없이 proxy 객체 사용
Content contentRef = contentRepository.getReferenceById(requestDto.getContentId());
// findById와 달리 SELECT 없이 프록시 참조만 반환 → 저장 시에만 사용 가능
```

---

### 지점 3: CalendarService, StatisticsService — userId 미분리 구조

**현재 코드:**
```java
// Content.java
@Column(name = "user_id")
private Long userId = 1L;  // 하드코딩
```
현재 userId가 하드코딩(`1L`)되어 있어 모든 사용자 데이터를 함께 조회함.
Member 인증이 완성되면 `userId`로 필터링 쿼리를 추가해야 하는데,
이때 인덱스가 없으면 전체 테이블 스캔 발생.

---

## 2. 인덱스 추가가 필요한 쿼리

### 현재 인덱스 현황
JPA `ddl-auto: update`로 자동 생성되는 인덱스는 PK와 `@Column(unique=true)` 컬럼뿐.
아래 쿼리들은 인덱스 없이 실행되고 있음.

---

### 인덱스 1: content.view_date

**쿼리:**
```sql
-- ContentRepository 다수 사용
WHERE EXTRACT(YEAR FROM view_date) = :year
WHERE TO_CHAR(view_date, 'YYYY-MM') = :yearMonth
WHERE EXTRACT(YEAR FROM view_date) = :year AND EXTRACT(MONTH FROM view_date) = :month
```

**문제**: 함수(`EXTRACT`, `TO_CHAR`)를 컬럼에 적용하면 일반 B-Tree 인덱스 사용 불가.

**해결 방법:**
```sql
-- PostgreSQL 함수 기반 인덱스 생성
CREATE INDEX idx_content_view_year ON content (EXTRACT(YEAR FROM view_date));
CREATE INDEX idx_content_view_yearmonth ON content (TO_CHAR(view_date, 'YYYY-MM'));
```

또는 쿼리를 범위 조건으로 변경해 일반 인덱스 활용:
```java
// JPQL 변경 예시 (TO_CHAR 대신 between 사용)
@Query("SELECT c FROM Content c WHERE c.viewDate >= :start AND c.viewDate < :end")
List<Content> findByViewDateBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
```

---

### 인덱스 2: content.user_id

**필요 시점**: Member 인증 완성 후 userId 필터링 쿼리 추가 시

```sql
CREATE INDEX idx_content_user_id ON content (user_id);
```

JPA에서 인덱스 선언:
```java
@Table(name = "content", indexes = {
    @Index(name = "idx_content_user_id", columnList = "user_id"),
    @Index(name = "idx_content_view_date", columnList = "view_date")
})
```

---

### 인덱스 3: content.external_id + category (복합 인덱스)

**쿼리:**
```java
// ContentRepository
Optional<Content> findByExternalIdAndCategory(String externalId, Category category);
```
중복 저장 방지 체크에 매번 사용됨.

```sql
CREATE UNIQUE INDEX idx_content_external_category ON content (external_id, category);
```
Unique 인덱스로 만들면 DB 레벨에서도 중복 삽입 방지.

---

### 인덱스 4: members.username (이미 unique 인덱스 존재)

```java
@Column(nullable = false, unique = true)
private String username;
```
`unique = true`는 자동으로 unique 인덱스를 생성하므로 추가 작업 불필요.

---

## 3. 캐싱 적용 고려 지점

### 캐싱이 효과적인 조건
- 읽기 빈도 높고 변경 빈도 낮은 데이터
- 계산 비용이 높거나 외부 API를 호출하는 로직
- 동일 파라미터로 반복 호출되는 메서드

---

### 캐싱 1: 외부 검색 API 결과 (우선순위 높음)

**현재 코드:**
```java
// SearchService.java
public List<UnifiedSearchResponse> search(Category category, String query) {
    // 매 요청마다 Kakao/TMDB/Jikan API 호출
    return providers.stream()
            .filter(p -> p.supports(category))
            .flatMap(p -> p.search(category, query).stream())
            ...
}
```

**문제**: 동일 키워드를 여러 사용자가 검색할 때마다 외부 API를 반복 호출.
Kakao/TMDB API는 호출 건수 제한(Rate Limit)이 있어 과호출 시 429 오류 발생 가능.

**해결 방법 (Spring Cache + Caffeine):**
```java
// build.gradle에 의존성 추가
implementation 'com.github.ben-manes.caffeine:caffeine'
implementation 'org.springframework.boot:spring-boot-starter-cache'
```
```java
// SearchService.java
@Cacheable(value = "searchResults", key = "#category + ':' + #query")
public List<UnifiedSearchResponse> search(Category category, String query) {
    ...
}
```
```yaml
# application.yml
spring:
  cache:
    caffeine:
      spec: maximumSize=500,expireAfterWrite=30m  # 30분 캐시
```

---

### 캐싱 2: 통계 데이터 (월별 집계)

**현재 코드:**
```java
// ContentRepository
List<CategoryStatInterface> countByCategoryInterface(@Param("yearMonth") String yearMonth);
Double getAverageRating(@Param("yearMonth") String yearMonth);
long countByMonth(@Param("yearMonth") String yearMonth);
```
통계 API는 매 요청마다 GROUP BY, AVG, COUNT 등 집계 쿼리를 실행.
같은 달의 데이터는 당월이 지나면 변하지 않으므로 캐싱 효과가 큼.

**해결 방법:**
```java
// StatisticsService에 캐시 적용 예시
@Cacheable(value = "statistics", key = "#yearMonth")
public StatisticsResponse getStatistics(String yearMonth) {
    ...
}

// 새 콘텐츠 저장 시 해당 월 캐시 무효화
@CacheEvict(value = "statistics", key = "#yearMonth")
public void saveContent(ContentRequestDto dto) {
    ...
}
```

---

### 캐싱 3: 캘린더 활성 날짜 (사이드바)

**현재 코드:**
```java
// ContentRepository
List<Integer> findActiveDays(@Param("userId") Long userId,
                              @Param("year") int year,
                              @Param("month") int month);
```
사이드바 캘린더에서 매 페이지 로드 시 호출될 수 있음.
```java
@Cacheable(value = "activeDays", key = "#userId + ':' + #year + ':' + #month")
public List<Integer> getActiveDays(Long userId, int year, int month) { ... }
```

---

## 4. 기타 최적화 고려 사항

### findAll() 대신 페이징 적용

**현재 코드:**
```java
// ContentService
contents = contentRepository.findAll();  // 전체 조회 → 데이터 증가 시 메모리 문제
```

**해결 방법:**
```java
// Pageable 파라미터 추가
Page<Content> findAll(Pageable pageable);

// 사용 예시
Pageable pageable = PageRequest.of(0, 20, Sort.by("viewDate").descending());
Page<Content> page = contentRepository.findAll(pageable);
```

---

### Highlight 저장 시 Content 재조회 최소화

현재 `saveHighlight()`는 Content를 `findById()`로 조회하는데,
저장만이 목적이라면 `getReferenceById()`로 SELECT 없이 외래 키만 설정 가능:
```java
// SELECT 없이 프록시 참조만 생성 (FK 설정용)
Content content = contentRepository.getReferenceById(requestDto.getContentId());
```

주의: `getReferenceById()`는 실제 데이터 접근 없이 ID만으로 참조를 생성하므로
존재하지 않는 ID를 주면 저장 시점에 `EntityNotFoundException` 발생.
비즈니스 로직상 존재 여부 검증이 필요하다면 `findById()`를 유지할 것.
