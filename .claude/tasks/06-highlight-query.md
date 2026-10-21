# 06. 하이라이트 모음집 조회 API (backend)

- 브랜치: `main` (작업 시작 시점 브랜치. 커밋하지 않음)
- 참고: `.claude/decisions.md` "확정된 설계 결정", `.claude/backlog.md` "GET /api/highlights 모음집 API", `frontend/lib/api.ts:104-110` `getHighlights`

## 목표
"나만의 문장 모음집" 화면이 쓸 하이라이트 목록 조회 API를 만든다.
- `GET /api/highlights?year=2026&category=ANIME`
- `year`, `category`는 모두 선택이다. 없으면 전체를 조회한다.
- 정렬은 `createdAt desc`, 같으면 `id desc`.
- 응답은 `HighlightResponseDto`(record) 목록: `id, text, page, season, episode, timestamp, createdAt, contentId, contentTitle, contentCategory`

## 설계 방향 (확정)
- 동적 조건은 Specification으로 만든다. `HighlightSpecifications.viewYear`, `categoryIn`은 조건이 null이면 null을 반환하고, `and()`가 그 조건을 무시한다.
  - JPQL `:p IS NULL OR ...`은 PostgreSQL에서 null 파라미터 타입 추론 오류가 날 수 있다. if 분기는 조건 조합마다 메서드가 늘어난다.
- 연도 필터는 `YEAR(col)` 대신 범위 비교 `[해당 연도 1/1, 다음 해 1/1)`로 한다. (인덱스 활용, 경계 검증)
- 연도 기준은 하이라이트 `createdAt`이 아니라 부모 `Content.viewDate`다.
- `HighlightRepository`에 `JpaSpecificationExecutor`를 추가한다. `findAll(Specification, Sort)`를 오버라이드해 `@EntityGraph(attributePaths = "content")`를 붙인다(N+1 방지).
- category 해석은 `HighlightService`의 static 메서드 `resolveCategories`가 한다.
  - null/ALL → 필터 없음
  - ANIME → ANIME + ANIME_MOVIE + ANIME_TVA (TMDB 애니가 ANIME_MOVIE/ANIME_TVA로 저장됨)
  - 나머지 → 단일 카테고리
- 연도 검증은 컨트롤러 `@Validated` 대신 서비스에서 `IllegalArgumentException`을 던진다(1900~2100). 기존 `GlobalExceptionHandler`가 400으로 처리한다.

## 테스트
- `HighlightFilterRepositoryTest` (신규, `@DataJpaTest` + 임베디드 H2)
  - 기존 `HighlightRepositoryTest`처럼 `Replace.NONE`으로 Supabase에 붙지 않는다. 기존 테스트 파일은 수정하지 않는다.
  - 내부 `@Configuration`에 `@EnableJpaAuditing`, `@EntityScan`, `@EnableJpaRepositories`를 지정한다. 테스트용 `DemoApplication`이 `ApiConfig`를 끌어와 슬라이스가 깨지는 것을 막기 위해서다.
  - 검증: 필터 없음 / 연도 경계(12/31 vs 1/1) / 단일 카테고리 / 애니 묶음 / 연도+카테고리 AND / 빈 결과 / EntityGraph로 content 로딩 확인(`PersistenceUnitUtil.isLoaded`)
- `HighlightQueryServiceTest` (신규, Mockito): DTO 매핑 / 빈 결과 / 비정상 연도 거절(repository 호출 안 함) / `resolveCategories`
- `./gradlew test` 전체 통과 확인

## DoD
- [ ] `GET /api/highlights`가 year/category 조합별로 필터링된 목록을 정렬해서 반환한다
- [ ] 범위 밖 연도는 400(`ErrorResponse`)으로 응답한다
- [ ] 목록 조회 시 content를 하이라이트마다 따로 조회하지 않는다(EntityGraph)
- [ ] 새 테스트 2개 파일 통과, `./gradlew test` 전체 통과
- [ ] decisions.md에 없는 결정만 추가, 발견 사항 기록

## 수정 허용 범위
- demo main: `repository/HighlightRepository.java`, `repository/HighlightSpecifications.java`(신규), `dto/response/HighlightResponseDto.java`(신규), `service/HighlightService.java`, `controller/HighlightController.java`
- demo test: `repository/HighlightFilterRepositoryTest.java`(신규), `service/HighlightQueryServiceTest.java`(신규)
- 문서: 이 파일, `.claude/decisions.md`(추가만), 발견 사항 기록 위치(기존 관례)

## 범위 밖
프론트 연동(타입/프록시), `ContentService.getFilteredContents`, `GlobalExceptionHandler`, `CalendarController`, 기존 테스트 파일, 인증/JWT, 페이지네이션

---

## 구현 내용
- `repository/HighlightSpecifications.java` (신규)
  - `viewYear(Integer year)`: `content.viewDate >= year-01-01 AND content.viewDate < (year+1)-01-01`. year가 null이면 null을 반환한다.
  - `categoryIn(Collection<Category>)`: `content.category IN (...)`. null이거나 비어 있으면 null을 반환한다.
  - 두 조건 모두 `root.get("content")`로 부모를 따라간다. 하이라이트 자체에는 연도/카테고리 컬럼이 없다.
- `repository/HighlightRepository.java`: `JpaSpecificationExecutor<Highlight>`를 추가했다. `findAll(Specification, Sort)`를 오버라이드하고 `@EntityGraph(attributePaths = "content")`를 붙였다. 기존 `findByContentId`는 그대로 둔다.
- `dto/response/HighlightResponseDto.java` (신규 record): `from(Highlight)`에서 content를 평평하게 펼친다. content가 null인 하이라이트(DB상 `content_id`가 nullable)는 `contentId/Title/Category`를 null로 내린다.
- `service/HighlightService.java`
  - `getHighlights(Integer year, Category category)`: 연도가 1900~2100 밖이면 repository를 부르기 전에 `IllegalArgumentException`을 던진다. 그다음 `Specification.where(viewYear).and(categoryIn(resolveCategories(category)))`를 `Sort.by(desc createdAt, desc id)`와 함께 조회한다.
  - `static resolveCategories(Category)`: null/ALL → null, ANIME → `[ANIME, ANIME_MOVIE, ANIME_TVA]`, 나머지 → `[category]`. 테스트에서 직접 부를 수 있게 package-private이다.
  - 클래스에 이미 `@Transactional(readOnly = true)`가 있어서 조회 메서드에는 따로 붙이지 않았다.
- `controller/HighlightController.java`: `@GetMapping`에 `@RequestParam(required = false) Integer year, Category category`를 받는다. 검증 어노테이션은 붙이지 않았다(서비스가 검증).

### 실제 SQL (H2 테스트 로그 기준)
- 조건 없음: `from highlight h1_0 left join content c1_0 on ... order by h1_0.created_at desc, h1_0.id desc` (쿼리 1번)
- 조건 있음: `from highlight h1_0 join content c1_0 on ... where c1_0.category in (?, ?, ?) order by ...`
  - Specification이 만든 암묵적 조인과 EntityGraph의 fetch 조인이 **조인 하나로 합쳐졌다.** content 테이블을 두 번 조인하지 않는다.
  - 필터가 있으면 inner join이라 content가 없는 하이라이트는 빠진다. 연도·카테고리가 부모 기준이므로 의도에 맞다.

## 검증 결과
### 서버 없이 확인한 것
- [x] `./gradlew clean test`: **75개 전부 통과**(실패 0, 에러 0, 스킵 0). 기존 60개 + 새 15개
  - `HighlightFilterRepositoryTest` 7개: 필터 없음(정렬 포함) / 연도 경계(25-12-31, 26-01-01, 26-12-31, 27-01-01 중 2026만) / 단일 카테고리 / 애니 묶음(TV 제외) / 연도+카테고리 AND / 빈 결과 / EntityGraph 로딩
  - `HighlightQueryServiceTest` 8개: DTO 매핑 / 정렬 인자(createdAt desc, id desc) / 빈 결과 / 비정상 연도 4건(1899, 2101, 0, -1. repository `never()`) / `resolveCategories`
- [x] 새 리포지토리 테스트가 임베디드 H2로 도는 것 확인(로그 `jdbc:h2:mem:...`, `Database version: 2.3.232`)
- [x] 빨간불 확인: `@EntityGraph`를 잠시 주석 처리하면 `contentLoadedWithEntityGraph`만 실패한다(7개 중 1개). 확인 후 원래대로 복원했다.
- [ ] PostgreSQL에서 실행: 테스트는 H2라 Supabase에서 실행되는 SQL은 확인하지 않았다. 다만 파라미터가 모두 non-null `LocalDate`/enum으로 바인딩되는 구조라 null 타입 추론 문제는 생기지 않는다.

### 서버가 필요한 것 (은서 확인 시나리오)
백엔드(8080)를 띄운 상태에서 주소창으로 확인한다.
1. `http://localhost:8080/api/highlights` → 전체 하이라이트가 최근 저장 순으로 나온다. 각 항목에 `contentId`, `contentTitle`, `contentCategory`가 있다.
2. `http://localhost:8080/api/highlights?year=2026` → 부모 작품의 **감상일(viewDate)**이 2026년인 것만 나온다. 하이라이트를 언제 썼는지(createdAt)와는 무관하다.
3. `http://localhost:8080/api/highlights?category=ANIME` → `contentCategory`가 ANIME, ANIME_MOVIE, ANIME_TVA인 항목이 모두 나온다.
4. `http://localhost:8080/api/highlights?category=ALL` → 1번과 같은 결과
5. `http://localhost:8080/api/highlights?year=1800` → `400`, `{"status":400,"message":"연도는 1900~2100 사이여야 합니다. year=1800"}`
6. 서버 로그(show-sql)에서 목록 조회 시 `select ... from highlight ... join content ...`가 한 번만 찍히는지 본다. 하이라이트 개수만큼 `select ... from content where id=?`가 반복되면 N+1이다.

---

## 변경 파일 (커밋하지 않음)
- 수정: `demo/src/main/java/book_log/demo/repository/HighlightRepository.java`
- 신규: `demo/src/main/java/book_log/demo/repository/HighlightSpecifications.java`
- 신규: `demo/src/main/java/book_log/demo/dto/response/HighlightResponseDto.java`
- 수정: `demo/src/main/java/book_log/demo/service/HighlightService.java`
- 수정: `demo/src/main/java/book_log/demo/controller/HighlightController.java`
- 신규: `demo/src/test/java/book_log/demo/repository/HighlightFilterRepositoryTest.java`
- 신규: `demo/src/test/java/book_log/demo/service/HighlightQueryServiceTest.java`
- 신규: `.claude/tasks/06-highlight-query.md` (이 파일)
- 수정: `.claude/backlog.md` (발견 사항 3줄 추가만)
- 수정하지 않음: `.claude/decisions.md`. 이번 설계 방향 6개가 모두 "확정된 설계 결정"에 이미 있다. `.claude/CLAUDE.md`, `.claude/decisions.md`는 작업 전부터 수정되어 있었고, 이번 작업에서 건드리지 않았다.

## 발견 사항 (범위 밖, 기록만)
1. **`ContentService.getFilteredContents`는 category=ANIME일 때 `findByCategory(ANIME)`만 조회한다**(`ContentService.java:81-83`). `findByYearAndCategory`도 `c.category = :category` 단일 비교다. 그래서 TMDB로 저장한 애니(ANIME_MOVIE/ANIME_TVA)가 빠질 수 있고, 하이라이트 모음집과 `/api/contents/filter`의 "애니" 결과가 달라진다. 같은 리포지토리의 `YEAR(c.viewDate)` 비교도 decisions의 범위 비교 원칙과 다르다.
2. **기존 테스트 2개가 실제 Supabase(PostgreSQL)에 붙는다.** `HighlightRepositoryTest`(`Replace.NONE`)와 `DemoApplicationTests`(`@SpringBootTest`) 로그에 `Database version: 17.6`이 찍힌다(이번 실행과 05 실행 모두). 지금은 `application.yml`이 디스크에 있기 때문이다(00 조사 때는 없어서 H2였다). `@DataJpaTest`는 롤백하지만, `ddl-auto` 설정은 실제 DB 스키마에 적용될 수 있다. 오프라인이면 `./gradlew test`가 실패한다. CLAUDE.md의 "테스트는 실제 DB에 붙지 않게"와 어긋난다.
3. **프론트 타입과 응답 형태가 다르다.** `frontend/types/index.ts:107-120` `HighlightWithContent`는 `content: { id, title, category, thumbnailUrl? }` 중첩 객체를 기대한다. 이번 DTO는 요구사항대로 평평한 `contentId/contentTitle/contentCategory`이고 썸네일이 없다. 프론트 연동 때 타입과 DTO 중 하나를 맞춰야 한다. 하이라이트 API가 프록시를 쓰지 않는 문제는 백로그에 이미 있다.
4. (이미 기록됨, 중복 기록 안 함) `CalendarController`의 `@Validated`가 던지는 `ConstraintViolationException`은 `GlobalExceptionHandler`에 핸들러가 없어서 500이 된다. 이 내용은 `00-inventory.md:222`에 "코드상 추론, 미확인"으로 이미 있다. 잘못된 Category 문자열(`MethodArgumentTypeMismatchException`) → 500도 같은 줄에 있다. 새 API도 `?category=FOO`면 같은 경로로 500이 된다.

---

## 정리 (트러블슈팅용)

### 1. 문제 상황
모음집 화면은 연도·카테고리를 골라 하이라이트를 봐야 한다. 그런데 두 조건이 모두 선택이라 조합이 4가지(없음/연도/카테고리/둘 다)다. 게다가 조건은 하이라이트가 아니라 부모 `Content`의 컬럼이다. 목록에 작품 제목을 같이 보여 줘야 하므로 LAZY 연관관계의 N+1도 막아야 했다. "애니"는 저장 경로에 따라 카테고리 값이 3개로 나뉘어 있다.

### 2. 고려한 선택지
- A. 조합별 쿼리 메서드 4개 + 서비스 if 분기 (`ContentService.getFilteredContents` 방식)
- B. JPQL 하나에 `(:year IS NULL OR ...) AND (:category IS NULL OR ...)`
- C. Specification으로 조건을 조각내고, 값이 없으면 null을 반환해 `and()`가 건너뛰게 함

### 3. 선택 이유 (트레이드오프)
C를 골랐다. A는 조건이 하나 늘 때마다 메서드 수가 두 배가 된다. B는 쿼리 하나로 끝나지만, PostgreSQL이 null 파라미터의 타입을 추론하지 못해 오류가 날 수 있다. C는 실제로 들어온 조건만 WHERE에 붙고, 조건 조각을 따로 테스트할 수 있다. 대신 문자열 경로(`"content"`, `"viewDate"`)를 쓰므로 필드 이름을 바꾸면 컴파일이 아니라 실행 시점에 깨진다. 이 위험은 리포지토리 테스트로 막는다.
- 연도는 `YEAR(viewDate) = ?` 대신 `>= 1/1 AND < 다음 해 1/1`로 비교한다. 컬럼에 함수를 씌우지 않아 인덱스를 쓸 수 있다. 12/31과 1/1 경계는 테스트로 고정했다.
- N+1은 `findAll(Specification, Sort)` 오버라이드 + `@EntityGraph`로 막았다. fetch join을 JPQL로 따로 쓰면 Specification과 조합할 수 없다.

### 4. 막혔던 부분과 해결
- `@DataJpaTest`는 설정 클래스를 패키지 위쪽에서 찾는다. 그러면 test 폴더의 `DemoApplication`(`@SpringBootApplication`)이 잡혀서 슬라이스 밖 빈까지 끌려온다. 테스트 안에 `static @Configuration` 클래스를 두면 그 설정만 쓴다. 그래서 거기에 `@EnableJpaAuditing`(createdAt 채우기), `@EntityScan`, `@EnableJpaRepositories`를 직접 달았다.
- EntityGraph 테스트에서 저장 직후 바로 조회하면, 영속성 컨텍스트가 저장할 때 쓴 Content 객체를 그대로 돌려준다. 그러면 EntityGraph가 없어도 "로딩됨"으로 보인다. `em.flush(); em.clear();`로 비운 뒤 조회해야 실제 DB 조회 결과를 검사한다. `@EntityGraph`를 빼면 이 테스트만 실패하는 것으로 확인했다.
- 필터 조건의 조인과 EntityGraph의 fetch 조인 때문에 content를 두 번 조인할까 걱정했다. SQL 로그를 보니 Hibernate 6이 하나로 합쳤다.

### 개념 메모
- **Specification**: WHERE 조건 하나를 `(root, query, cb) -> Predicate` 객체로 만든 것이다. `where(a).and(b)`로 조립하고, null 조각은 건너뛴다. 그래서 "선택 조건"을 if 분기 없이 표현할 수 있다.
- **N+1과 EntityGraph**: `Highlight.content`가 LAZY면, 목록을 1번 조회한 뒤 `getContent().getTitle()`을 부를 때마다 content를 하나씩 더 조회한다(1 + N). `@EntityGraph(attributePaths = "content")`는 그 리포지토리 메서드를 실행할 때만 content를 left join fetch로 같이 가져온다. 엔티티의 LAZY 설정은 그대로 둔다.

---

## 복습 질문
<details>
<summary>1. <code>Specification.where(viewYear(null)).and(categoryIn(List.of(BOOK)))</code>가 만드는 WHERE 절은 무엇인가? <code>viewYear</code>가 null 대신 <code>cb.conjunction()</code>(항상 참)을 반환하게 바꾸면 결과와 SQL은 어떻게 달라지는가?</summary>

답:

</details>

<details>
<summary>2. <code>HighlightFilterRepositoryTest.contentLoadedWithEntityGraph</code>에서 <code>em.flush(); em.clear();</code>를 지우면 <code>@EntityGraph</code>가 없어도 테스트가 통과할 수 있다. 왜 그런가? 영속성 컨텍스트(1차 캐시) 관점에서 설명해 보자.</summary>

답:

</details>

<details>
<summary>3. (기존 코드 연결) 같은 "애니" 필터인데 <code>GET /api/contents/filter?category=ANIME</code>와 <code>GET /api/highlights?category=ANIME</code>의 결과 작품이 다를 수 있다. TMDB 검색으로 저장한 애니 작품의 category는 무엇으로 저장되는가? <code>SearchService</code>/<code>TmdbSearchService</code>를 근거로 설명해 보자.</summary>

답:

</details>
