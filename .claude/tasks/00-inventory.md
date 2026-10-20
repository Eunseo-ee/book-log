# 00. 코드베이스 전수조사 (2026-10-19)

> 이 작업에서 코드/설정 파일은 수정하지 않았음. 실행한 것: `git fetch origin`, `./gradlew test`, `./gradlew -q javaToolchains`, 파일 읽기.
> 설정 파일의 값은 출력하지 않고 키 이름만 적었음.

## 한눈에 보기
1. 현재 브랜치는 `feature/frontend`이며 `origin/feature/frontend`, `origin/main`과 같은 커밋(5030134)이다. 로컬 `main`은 origin보다 17커밋 뒤처져 있다.
2. fetch 후에도 로컬에 없는 원격 커밋은 `origin/feat/security`의 5개(JWT/회원가입, docs/*.md)뿐이다.
3. 백엔드에는 `UnifiedSearchResponse.externalId`, `isSaved`가 **이미 구현되어 있다**. JSON 키도 각각 `externalId`, `isSaved`이다 (커밋 3aca910, d632857).
4. `isSaved`는 SearchService에서 캐시 바깥에서 **IN 일괄 조회 1회**로 채운다 (`findByExternalIdIn`).
5. Provider 상태: Kakao는 활성(externalId=ISBN13 우선), TMDB는 활성(externalId=TMDB id, Redis 캐시 적용), AniList는 파일 전체가 주석 처리되어 비활성이다.
6. `application.yml`이 디스크에 없다. `application-api-key.yml`만 있고, 이 파일은 활성화하는 설정도 없다.
7. 프론트엔드는 `frontend/`에 있다 (Next.js 15, React 19, Tailwind 3). 화면은 `/`, `/search`, `/calendar`, `/highlights` 4개다.
8. 프론트에는 저장 폼과 '저장됨' 표시가 **없다**. 프론트 타입(`types/index.ts`)에도 `externalId`, `isSaved`가 없다.
9. `./gradlew test` 결과 **55개 모두 통과**(실패 0, 스킵 0). 다만 테스트 폴더의 `DemoApplication.java`가 main의 같은 이름 클래스를 가리고 있다.
10. 문서와 코드 불일치: `decisions.md`에는 externalId/isSaved를 "추가할 것"으로 적혀 있지만 이미 구현됐다. 프론트 `lib/api.ts`의 검색/하이라이트 API 명세는 백엔드와 다르다.

---

## 0. Git 상태

### 브랜치
| 브랜치 | 커밋 | upstream | 앞섬/뒤처짐 |
|---|---|---|---|
| `feature/frontend` (현재) | 5030134 | origin/feature/frontend | 0 / 0 |
| `main` | 2b8fe0f | origin/main | 0 / **17 뒤처짐** |

원격 브랜치: `origin/main`(5030134), `origin/feature/frontend`(5030134), `origin/feat/security`(6f57a9b), `origin/feature/1-api-search`, `origin/feature/2-save-api`, `origin/feature/3-calendar-api`, `origin/feature/9-statistics`

- 근거: `git branch -a -vv`, `git rev-list --left-right --count`

### 커밋되지 않은 변경
추적 중인 파일 중 수정된 것은 없다. 추적되지 않은 파일은 아래 3개다.
- `.claude/CLAUDE.md`
- `.claude/decisions.md`
- `.claude/tasks/00-inventory.md` (이 파일. 조사 전에는 이번 요청문이 그대로 들어 있었고, 조사 결과로 덮어씀)

### 최근 커밋 10개 (feature/frontend)
| 해시 | 날짜 | 메시지 |
|---|---|---|
| 5030134 | 2026-10-17 19:36 | fix: Content.addHighlight 편의 메서드 추가 및 삭제 테스트 수정 |
| 830f601 | 2026-10-17 19:24 | test: 검색 시그니처 변경에 맞게 테스트 수정 |
| 80214ee | 2026-10-17 19:24 | fix: TMDB 검색 캐시 키에 category, page 포함 및 SearchResponse 역직렬화 지원 |
| 01d966a | 2026-10-17 18:55 | Merge branch 'main' into feature/frontend |
| 72867b3 | 2026-10-17 14:00 | wip: search DTO refactor and search UI work in progress |
| d632857 | 2026-10-17 01:25 | feat: 검색 결과의 isSaved를 캐시 바깥에서 배치 조회로 채우기 |
| 3aca910 | 2026-10-17 01:25 | feat: 검색 응답에 externalId, isSaved 필드 추가 및 Kakao/TMDB externalId 매핑 |
| 3cfe876 | 2026-10-16 02:12 | feat: Redis 캐싱을 TMDB 전용으로 정리하고 장애 대응 추가 |
| 0b019cb | 2026-10-15 19:34 | fix: .gitattributes의 gradlew 경로를 demo/gradlew로 수정 |
| 5f4a923 | 2026-10-15 19:28 | Merge pull request #11 from Eunseo-ee/feature/redis-cache |

### fetch 결과
- `git fetch origin`의 출력은 없었고 종료 코드는 0이다. 이번 fetch로 새로 받은 커밋은 없다. reflog상 `origin/main`의 마지막 갱신은 2026-10-19 18:21의 fetch fast-forward다.
- 로컬 브랜치 어디에도 없는 원격 커밋은 `origin/feat/security`의 5개뿐이다.
  - 0006231 Member 엔티티 및 레포지토리 추가 / 41418f9 JWT 토큰 생성·검증 및 인증 필터 / c5ba07c 회원가입·로그인 API / 7c90003 Spring Security JWT 설정 / 6f57a9b md들 추가 (마지막 커밋 2026-05-02)
  - 이 브랜치에만 있는 파일: `AuthController`, `Member`, `Role`, `MemberRepository`, `security/*`, `AuthService`, `LoginRequestDto`, `SignupRequestDto`, `TokenResponseDto`, `docs/architecture.md`, `docs/code-guide.md`, `docs/optimization.md`, `docs/tech-decisions.md`, `docs/troubleshooting.md`
- 나머지 원격 브랜치(feature/1, 2, 3, 9)는 로컬 브랜치에 모두 포함되어 있다(고유 커밋 0개).
- 로컬 `main`과 `origin/main`의 차이 17커밋은 5163c76(프론트 UI) ~ 5030134 구간이다.

---

## 1. 프로젝트 구조

```
book-log-main/
├── .claude/            CLAUDE.md, decisions.md, tasks/00-inventory.md   (모두 미추적)
├── .github/            ISSUE_TEMPLATE/{bug,feat,config}.yml, PULL_REQUEST_TEMPLATE.md
├── .gitattributes, .gitignore, README.md ("# book-log" 한 줄)
├── demo/               Spring Boot 백엔드
│   ├── build.gradle, settings.gradle, gradlew(.bat), gradle/wrapper (Gradle 9.2.1)
│   ├── HELP.md         (gitignore 대상, 미추적)
│   ├── bin/            IDE(Eclipse/VS Code) 빌드 산출물 (gitignore 대상, 미추적)
│   └── src/
│       ├── main/java/book_log/demo/
│       │   ├── DemoApplication.java
│       │   ├── config/      ApiConfig, CacheErrorConfig, RedisCacheConfig, SecurityConfig
│       │   ├── controller/  Calendar, Content, Highlight, Search, Statistics
│       │   ├── domain/      BaseTimeEntity, Category, Content, Highlight, Status
│       │   ├── dto/request/ ContentRequestDto, HighlightRequestDto
│       │   ├── dto/response/ AnimeResponse, CalendarResponse, ContentResponseDto, KakaoBookResponse,
│       │   │                 SearchResponse, StatisticsResponse, TmdbResponse, UnifiedSearchResponse
│       │   ├── exception/   ErrorResponse, GlobalExceptionHandler
│       │   ├── repository/  ContentRepository, HighlightRepository
│       │   └── service/     AnimeSearchService(전체 주석), Calendar, Content, Highlight, KakaoSearch,
│       │                    SearchProvider(인터페이스), Search, Statistics, TmdbSearch
│       ├── main/resources/  application-api-key.yml, static/(빈 폴더), templates/(빈 폴더)
│       └── test/java/book_log/demo/  (4장 참고)
└── frontend/           Next.js 프론트엔드 (3장 참고)
    ├── app/            layout.tsx, page.tsx, globals.css, search/, calendar/, highlights/
    ├── components/     calendar/, highlights/, layout/, search/, sidebar/
    ├── hooks/          useCalendar.ts, useHighlights.ts
    ├── lib/            api.ts, constants.ts
    ├── types/          index.ts
    ├── .env.local.example, next.config.js, tailwind.config.js, postcss.config.js, tsconfig.json
    ├── node_modules/ (설치됨), .next/ (이전 dev 빌드 산출물, gitignore 대상)
```

- 프론트엔드: **있음**. 위치는 `frontend/`
- 근거: `find`로 확인한 파일 목록, `.gitignore`

---

## 2. 백엔드 (demo/)

### 2-0. 빌드 환경
- Spring Boot 3.4.1, dependency-management 1.1.7, Java toolchain 21. 근거: `demo/build.gradle`
- 의존성: data-jpa, security, web, actuator, validation, data-redis, cache, springdoc-openapi 2.7.0, lombok, postgresql(runtime), h2(runtime), test, spring-security-test
- Gradle Wrapper 9.2.1. 근거: `demo/gradle/wrapper/gradle-wrapper.properties`
- PATH의 `java`는 17.0.12이다. Gradle이 인식한 툴체인은 Temurin 21.0.9, Oracle 17, OpenJDK 23이다(`./gradlew -q javaToolchains`).

### 2-1. 컨트롤러별 엔드포인트
| 컨트롤러 | 메서드 | 경로 | 파라미터 | 반환 타입 |
|---|---|---|---|---|
| SearchController | GET | `/api/search` | `category: Category`(필수), `query: String`(필수), `page: int`(기본값 1) | `ResponseEntity<SearchResponse>` |
| ContentController | POST | `/api/contents` | `@Valid @RequestBody ContentRequestDto` | `ResponseEntity<Long>` (201 Created, Location `/api/contents/{id}`) |
| ContentController | GET | `/api/contents/check` | `externalId: String`, `category: Category` (둘 다 필수) | `ResponseEntity<Boolean>` |
| ContentController | GET | `/api/contents/filter` | `year: Integer`(선택), `category: Category`(선택) | `ResponseEntity<List<ContentResponseDto>>` |
| HighlightController | POST | `/api/highlights` | `@Valid @RequestBody HighlightRequestDto` | `ResponseEntity<Long>` (200) |
| HighlightController | PUT | `/api/highlights/{id}` | `id: Long`, `@Valid @RequestBody HighlightRequestDto` | `ResponseEntity<Long>` |
| HighlightController | DELETE | `/api/highlights/{id}` | `id: Long` | `ResponseEntity<Void>` (204) |
| CalendarController | GET | `/api/calendar/activities` | `year: int`(2000~2100), `month: int`(1~12), `@Validated` | `ResponseEntity<CalendarResponse>` (userId는 1L로 고정) |
| StatisticsController | GET | `/api/statistics` | `year: Integer`(선택), `month: Integer`(선택) | `ResponseEntity<StatisticsResponse>` |

- `GET /api/highlights`(목록 조회)는 **없다**.
- `@CrossOrigin(origins="*")`은 StatisticsController에만 있다. SecurityConfig에는 CORS 설정이 없다.
- StatisticsController는 `month`가 선택 파라미터인데, null 기본값을 채우기 전에 `month < 1`을 먼저 비교한다(`StatisticsController.java:34`). `month`를 생략했을 때 실제 응답이 어떻게 되는지는 실행해 보지 않았다(미확인).
- 근거: `demo/src/main/java/book_log/demo/controller/*.java`

### 2-2. DTO 필드
**UnifiedSearchResponse**: `@Getter @Builder(toBuilder=true) @NoArgsConstructor @AllArgsConstructor`. 근거: `dto/response/UnifiedSearchResponse.java`
| 필드 | 타입 | JSON 키 |
|---|---|---|
| externalId | String | `externalId` |
| title | String | `title` |
| authorOrDirector | String | `authorOrDirector` |
| releaseDate | String | `releaseDate` |
| voteAverage | Double | `voteAverage` |
| thumbnailUrl | String | `thumbnailUrl` |
| category | Category | `category` |
| isSaved | **Boolean (래퍼 타입)** | `isSaved` |

- `isSaved`, `externalId`: **둘 다 있다**. `@JsonProperty`는 붙어 있지 않다.
- `isSaved`의 JSON 키: Lombok `@Getter`는 래퍼 타입 `Boolean`에 `getIsSaved()`를 만들고(테스트 코드에서 `getIsSaved()`를 호출하는 것으로 확인), Jackson은 이 getter를 `isSaved` 키로 직렬화한다. 원시 타입 `boolean`이었다면 `isSaved()` getter가 생겨 키가 `saved`가 됐을 것이다. 실제 HTTP 응답 JSON을 직접 확인하지는 않았다(**미확인**). 코드상 추론이다.

**SearchResponse**: `results: List<UnifiedSearchResponse>`, `currentPage: int`, `totalPages: int`, `totalResults: long`. 근거: `dto/response/SearchResponse.java`
- Kakao/TMDB Provider는 `currentPage`를 채우지 않으므로 기본값 0이 나간다. SearchService도 Provider가 준 값을 그대로 복사한다. 근거: `KakaoSearchService.java:47-51`, `TmdbSearchService.java:68-72`, `SearchService.java:53-58`

**ContentRequestDto**: 근거: `dto/request/ContentRequestDto.java`
| 필드 | 타입 | 검증 |
|---|---|---|
| title | String | `@NotBlank` |
| authorOrDirector | String | - |
| thumbnailUrl | String | - |
| externalId | String | - (있음) |
| category | Category | - (`@NotNull` 없음) |
| genre | String | - |
| viewDate | LocalDate | `@NotNull` |
| rating | Double | `@Min(0) @Max(5)` |
| comment | String | `@Size(max=500)` |
| status | Status | - |
- `isSaved` 없음.

**ContentResponseDto**: `id: Long`, `title: String`, `author: String`(← authorOrDirector), `category: Category`, `viewDate: LocalDate`, `rating: Double`, `imgUrl: String`(← thumbnailUrl). 근거: `dto/response/ContentResponseDto.java`
- `externalId`, `isSaved`, `status`, `comment`, `genre` **없음**. 엔티티 필드명과 JSON 키가 다르다(author, imgUrl).

**그 외**
- HighlightRequestDto: `text`(`@NotBlank`, `@Size(max=1000)`), `contentId`(`@NotNull`), `page`(`@Min(1)`), `season`, `episode`, `timestamp`
- CalendarResponse(record): `year`, `month`, `days: List<DayActivity(day, thumbnail, count)>`
- StatisticsResponse: `targetMonth`, `categoryStats[{category,count}]`, `genreStats[{genre,count}]`, `averageRating`, `activity{currentCount,previousCount,growthRate}`, `mostActiveDay`, `topCategory`, `tasteAnalysis`
- 외부 API 응답용 DTO: `KakaoBookResponse`(documents[title, authors, publisher, datetime, isbn, price, thumbnail, status], meta[is_end, pageable_count, total_count]), `TmdbResponse`(results[id, media_type, title, name, genre_ids, poster_path, release_date, first_air_date, vote_average, overview], total_pages, total_results), `AnimeResponse`(AniList용. 클래스는 남아 있으나 사용처 없음)

### 2-3. 검색 Provider 상태
| Provider | 파일 | @Service | externalId | 출처 | 캐시 | 예외 처리 |
|---|---|---|---|---|---|---|
| Kakao | `service/KakaoSearchService.java` | **있음 (활성)** | 채움 | 카카오 `isbn` 필드("ISBN10 ISBN13" 공백 구분)에서 길이 13인 토큰을 우선 사용하고, 없으면 첫 토큰, 비어 있으면 null (`pickIsbn`) | 없음 | try/catch 없음 |
| TMDB | `service/TmdbSearchService.java` | **있음 (활성)** | 채움 | TMDB `id`(Long)를 `String.valueOf`로 변환, null이면 null | `@Cacheable("tmdbSearch", key=query:category:page)`, 빈 결과는 캐시하지 않음, TTL 10분(Redis) | catch(Exception) 후 빈 SearchResponse 반환 |
| AniList | `service/AnimeSearchService.java` | **없음 (파일 전체가 `//` 주석, package 선언 포함)** | 채우지 않음 (주석 처리된 코드에도 externalId 매핑 없음) | - | - | - |

- `supports()`: Kakao는 BOOK과 ALL. TMDB는 TV, MOVIE, ANIME_MOVIE, ANIME_TVA, ANIME, ALL.
- ANIME 검색은 SearchService가 TMDB 결과를 ANIME_MOVIE/ANIME_TVA로만 거른다(`SearchService.java:69-86`).
- Kakao는 `page` 인자를 URL에 넣지 않는다(`KakaoSearchService.java:37`). totalPages는 `total_count / 20`으로 계산한다(`:45`).
- ALL 검색은 모든 Provider를 호출한 뒤 voteAverage 내림차순으로 합친다. Kakao 결과는 voteAverage가 null이라 뒤로 간다(`SearchService.java:96-117`).
- 근거: 위 파일들, `config/ApiConfig.java`(kakaoKey, tmdbToken, RestTemplate 타임아웃 3초)

### 2-4. SearchService의 isSaved 세팅
- **있다.** `SearchService.search()` → `searchProviders()`(Provider 호출, TMDB는 캐시를 거침) → `markSavedStatus()`. 근거: `service/SearchService.java:26-59`
- 방식: 결과에서 null이 아닌 externalId를 중복 없이 모은 뒤 `ContentService.findSavedKeys()`를 **1회** 호출한다. 이 메서드는 `ContentRepository.findByExternalIdIn(Collection)`, 즉 **IN 일괄 조회**를 쓰며 `SavedKeyInterface` 프로젝션으로 externalId와 category만 가져온다. 이후 `(externalId, category)` 쌍(`SavedKey` record)이 일치하는지 메모리에서 판단한다.
- 원본 객체는 그대로 두고 `toBuilder()`로 만든 복사본에 isSaved를 넣는다(캐시된 객체를 오염시키지 않기 위함).
- 결과가 비었거나 null이면 DB를 조회하지 않는다. externalId가 null인 항목은 `isSaved=false`가 된다.
- 근거: `service/ContentService.java:26-42`, `repository/ContentRepository.java:28-43`

### 2-5. Repository 쿼리 메서드
**ContentRepository** (`JpaRepository<Content, Long>`). 근거: `repository/ContentRepository.java`
| 메서드 | 방식 |
|---|---|
| `Optional<Content> findByExternalIdAndCategory(String, Category)` | 메서드 이름 기반 쿼리 |
| `List<SavedKeyInterface> findByExternalIdIn(Collection<String>)` | 메서드 이름 기반 쿼리 + 프로젝션 |
| `List<Content> findByYearAndCategory(int, Category)` | JPQL `YEAR(c.viewDate)` |
| `List<Content> findByYear(int)` | JPQL |
| `List<Content> findByCategory(Category)` | 메서드 이름 기반 쿼리 |
| `List<Integer> findActiveDays(Long userId, int year, int month)` | native, `EXTRACT(...)` |
| `List<CategoryStatInterface> countByCategoryInterface(String yearMonth)` | JPQL `TO_CHAR(..., 'YYYY-MM')`, `STR(c.category)` |
| `List<GenreStatInterface> countByGenreInterface(String yearMonth)` | JPQL |
| `Double getAverageRating(String yearMonth)` | JPQL |
| `long countByMonth(String yearMonth)` | JPQL |
| `Object[] findMostActiveDay(String yearMonth)` | native, `EXTRACT(DOW ...) ... LIMIT 1` |

**HighlightRepository**: `List<Highlight> findByContentId(Long)`. 이 메서드는 테스트에서만 사용되고, 서비스나 컨트롤러에서는 쓰이지 않는다. 근거: `repository/HighlightRepository.java`, Grep 결과

- 엔티티 `Content`에는 externalId/category에 unique 제약이나 인덱스 선언이 없다. 근거: `domain/Content.java`

### 2-6. 예외 처리 (GlobalExceptionHandler)
근거: `exception/GlobalExceptionHandler.java`, `exception/ErrorResponse.java`
| 예외 | HTTP | 응답 본문 |
|---|---|---|
| IllegalStateException (중복 저장 등) | 400 | `ErrorResponse{status, message}` (message는 e.getMessage()) |
| IllegalArgumentException | 400 | `ErrorResponse{status, message}` |
| DateTimeException | 400 | `ErrorResponse{status, "날짜 형식이 올바르지 않습니다. 월은 1-12 사이여야 합니다."}` (고정 문구) |
| MethodArgumentNotValidException (@Valid 실패) | 400 | `Map<String,String>` {필드명: 메시지} (ErrorResponse 형태가 아님) |
| Exception (그 외 전부) | 500 | `ErrorResponse{500, "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."}` + printStackTrace |

- 별도 핸들러가 없는 예외: `ConstraintViolationException`(CalendarController의 `@Min/@Max`), `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException`(잘못된 Category 문자열 등). 이 예외들은 모두 `Exception` 핸들러로 가서 500이 된다고 코드상 추론된다. 실행해서 확인하지는 않았다(미확인).
- 중복 저장(`IllegalStateException`)은 409가 아니라 400으로 응답한다.
- `ContentService.saveContent`는 `requestDto.getRating() < 0`에서 `Double`을 언박싱하므로, rating이 null이면 NPE가 나고 500으로 응답할 것으로 보인다(`ContentService.java:48`). 실행해서 확인하지는 않았다.

### 2-7. 설정 파일
| 파일 | 디스크 | git 추적 | 키 이름 |
|---|---|---|---|
| `demo/src/main/resources/application.yml` | **없음** | 미추적 (`.gitignore:39`, 커밋 2cc8a22에서 추적 해제) | - |
| `demo/src/main/resources/application-api-key.yml` | 있음 | 미추적 (`.gitignore:40`) | `api.kakao-key`, `api.tmdb-token`, `spring.datasource.password` |
| `demo/bin/main/application-api-key.yml` | 있음 (IDE 산출물) | 미추적 | 위와 같은 키 |

- 추적 해제 직전(2cc8a22^) `application.yml`의 키: `spring.application.name`, `spring.profiles.include`(값: `api-key`), `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.driver-class-name`(값: PostgreSQL 드라이버), `spring.jpa.hibernate.ddl-auto`(값: update), `spring.jpa.show-sql`, `spring.jpa.properties.hibernate.format_sql`. Redis 관련 키(`spring.data.redis.*`)는 없었다.
- 지금 디스크에는 `application.yml`이 없어서 `spring.profiles.include: api-key`도 없다. 따라서 `application-api-key.yml`은 기본 실행 시 로드되지 않는다. 테스트 로그에서도 `No active profile set, falling back to 1 default profile: "default"`가 찍혔고, H2 인메모리 DB(`jdbc:h2:mem:...`)로 기동됐다.
- 근거: `ls demo/src/main/resources`, `git check-ignore -v`, `git show 2cc8a22^:demo/src/main/resources/application.yml`(키만 확인), `build/test-results/test/TEST-book_log.demo.DemoApplicationTests.xml`

---

## 3. 프론트엔드 (frontend/)

### 3-1. 프레임워크와 의존성
근거: `frontend/package.json`, `node_modules/*/package.json`
- Next.js(App Router) `^15.0.0` → 설치된 버전 15.5.15
- React / React-DOM `^19.0.0` → 설치된 버전 19.2.5
- lucide-react 0.483.0 (아이콘)
- dev: TypeScript ^5 (설치 5.9.3), Tailwind CSS ^3.4.0 (설치 3.4.19), PostCSS, Autoprefixer, @types/*
- 빌드 도구: Next 기본(`next dev/build/start`). 테스트, ESLint, 상태관리, 데이터 패칭 라이브러리는 없다.
- tsconfig: `strict: true`, 경로 별칭 `@/*` → `./*`

### 3-2. 폴더/컴포넌트 구조와 네이밍
```
app/            layout.tsx(Sidebar + Header + main), page.tsx, search/page.tsx, calendar/page.tsx, highlights/page.tsx
components/
  calendar/CalendarView.tsx         ('use client', RecapCard 내부 컴포넌트 포함)
  highlights/HighlightList.tsx, HighlightCard.tsx
  layout/Header.tsx                 (SearchForm 포함, sticky)
  search/SearchForm.tsx
  sidebar/Sidebar.tsx, MiniCalendar.tsx
hooks/          useCalendar.ts (useMiniCalendar, useCalendar), useHighlights.ts
lib/            api.ts (fetch 래퍼 + 엔드포인트 함수), constants.ts (라벨, 색상 매핑)
types/index.ts  백엔드 DTO에 대응하는 TS 타입
```
- 네이밍: 컴포넌트는 PascalCase `.tsx`, 기능별 폴더로 나눔, 페이지는 `app/<route>/page.tsx`, 훅은 `useXxx.ts`, `export default function`.
- 세미콜론 사용이 섞여 있다. 대부분 파일은 세미콜론 없이 작은따옴표를 쓰고, `search/page.tsx`, `SearchForm.tsx`, `Header.tsx`는 세미콜론을 쓴다.

### 3-3. 스타일링과 디자인 톤
근거: `tailwind.config.js`(theme 확장 없음), `app/globals.css`(Tailwind 지시어 + box-sizing만), `lib/constants.ts`, 각 컴포넌트
- 방식: Tailwind 유틸리티 클래스만 사용한다. CSS 모듈이나 styled 라이브러리는 없다. `dark:` 변형을 쓰지만 `darkMode` 설정이 없으므로 기본값(media, OS 설정 따름)이다.
- 색상: 베이스는 neutral(배경 `bg-neutral-50`/다크 `neutral-950`), 포인트 색은 **violet**(로고, 활성 메뉴, 포커스 링, 오늘 날짜, 다음 버튼 `bg-violet-600`)이다.
  - 카테고리 색: BOOK=violet, MOVIE/TV=orange, ANIME 계열=emerald (배지 `bg-*-100 text-*-800`, 강조선 `border-*-500`, 점 `bg-*-500`)
  - 증감 표시: 상승 emerald-600, 하락 rose-500. 일요일 표시는 rose-400.
- 레이아웃: 고정 사이드바 `w-56`, 본문 `ml-56`, 콘텐츠 영역 `max-w-4xl mx-auto px-6 py-8`, 헤더 `h-16`(`bg-white/80 backdrop-blur-md`).
- 카드: `bg-white dark:bg-neutral-950 border border-neutral-200 dark:border-neutral-800 rounded-2xl p-5`(하이라이트, 캘린더). RecapCard는 `rounded-xl p-4`. 검색 결과 카드만 `rounded-lg shadow-sm p-4`로 톤이 다르다.
- 버튼: 아이콘 버튼은 `p-2 rounded-lg hover:bg-neutral-100`. 탭은 `bg-neutral-100 rounded-xl p-1` 안에 활성 `bg-white shadow-sm`. 주 버튼은 `bg-violet-600 text-white rounded-lg px-4 py-2`. 보조 버튼은 `border border-neutral-300 bg-white rounded-lg`.
- 간격: `gap-1~6`, 글자 크기는 `text-[11px]`~`text-xl`의 작은 크기 위주.
- 이미지: 모두 `<img>` 태그를 쓴다(`next/image` 미사용). `next.config.js`의 `images.remotePatterns`(kakao, themoviedb, anilist)는 쓰이지 않는다.

### 3-4. API 호출 방식과 백엔드 주소
- `lib/api.ts`: `fetch` 래퍼 `request<T>()`를 통해 `BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'`로 **백엔드에 직접 요청**한다. 응답이 ok가 아니면 `Error`를 던지고, 204면 undefined를 반환한다.
- `next.config.js`: `/api/:path*`를 `${NEXT_PUBLIC_API_URL ?? localhost:8080}/api/:path*`로 넘기는 rewrites 프록시가 있다. 그런데 `lib/api.ts`는 절대 주소로 요청하므로 **이 프록시를 거치지 않는다**.
- `app/search/page.tsx`: 서버 컴포넌트가 `http://localhost:8080/api/search?...`를 **하드코딩**해 fetch한다(`cache: 'no-store'`). lib/api.ts를 쓰지 않는다.
- `.env.local.example`: `NEXT_PUBLIC_API_URL` 키 하나. 실제 `.env.local` 파일은 없다.
- 호출 위치: 클라이언트 훅(`useCalendar`, `useHighlights`), `CalendarView`(statistics), `HighlightList`(delete), 서버 컴포넌트 `search/page.tsx`.

### 3-5. 화면, 라우트, 호출 API
| 라우트 | 파일 | 호출 API | 백엔드 존재 여부 |
|---|---|---|---|
| `/` | `app/page.tsx` | 없음 (정적 안내 문구) | - |
| `/search?category&query&page` | `app/search/page.tsx` | `GET /api/search?category=&query=&page=` (직접 fetch) | 있음 |
| `/calendar` | `app/calendar/page.tsx` → `CalendarView` | `GET /api/calendar/activities`, `GET /api/statistics` | 있음 |
| `/highlights` | `app/highlights/page.tsx` → `HighlightList` | `GET /api/highlights?year&category`, `DELETE /api/highlights/{id}` | GET은 **없음**, DELETE는 있음 |
| (모든 화면) Sidebar의 MiniCalendar | `components/sidebar/MiniCalendar.tsx` | `GET /api/calendar/activities` | 있음 |
| (모든 화면) Header의 SearchForm | `components/search/SearchForm.tsx` | 호출 없이 `/search`로 router.push | - |

- `lib/api.ts`에 정의만 되어 있고 **어디서도 호출하지 않는** 함수: `searchContents`, `saveContent`, `getFilteredContents`, `checkDuplicate`, `saveHighlight`, `updateHighlight` (Grep 결과)
- `lib/api.ts`의 `searchContents`는 `?keyword=`와 `UnifiedSearchResponse[]`를 가정한다. 백엔드는 `query`(필수), `page`, `SearchResponse` 객체다. 실제로는 쓰이지 않는다.
- `search/page.tsx`의 props 타입은 `searchParams`를 동기 객체로 선언했다. Next 15에서 `searchParams`는 Promise다. 런타임 경고나 동작은 실행해 보지 않아 미확인이다.

### 3-6. 검색 화면 / 저장 폼 / '저장됨' 표시
- 검색 화면: **있다** (`app/search/page.tsx`). 결과 카드(썸네일, 카테고리 배지, 제목, 저자/감독, 출시일, 평점)와 이전/다음 페이지 이동이 있다. 카드에는 클릭 동작이나 링크가 없다.
- 저장 폼('기록하기'): **없다**. `saveContent`를 호출하는 컴포넌트가 없다.
- '저장됨' 표시: **없다**. `isSaved`를 참조하는 코드가 frontend에 없다(Grep 결과 0건).
- 프론트 타입 `UnifiedSearchResponse`(`types/index.ts:11-18`)에 `externalId`, `isSaved`가 **없다**. `ContentRequestDto` 타입에는 `externalId?`가 있다.
- 하이라이트 수정 UI: `HighlightCard`에 수정 버튼은 있지만 `HighlightList`가 `onEdit`을 넘기지 않아 아무 동작도 하지 않는다.

---

## 4. 테스트

### 4-1. 테스트 파일과 대상
근거: `demo/src/test/java/book_log/demo/**`
| 파일 | 대상 / 방식 | 테스트 수 |
|---|---|---|
| `DemoApplication.java` | **테스트가 아님.** main과 같은 FQCN `book_log.demo.DemoApplication`을 가진 별도 클래스 (`@SpringBootApplication`, CommandLineRunner로 "데이터 저장 테스트 시작!" 출력) | - |
| `DemoApplicationTests.java` | `@SpringBootTest` contextLoads | 1 |
| `repository/HighlightRepositoryTest.java` | `@DataJpaTest` + `Replace.NONE`, Highlight/Content 저장·조회·수정·cascade 삭제 | 5 |
| `service/CacheFailureFallbackTest.java` | 실제 RedisCacheConfig + CacheErrorConfig를 쓰고, 아무도 listen하지 않는 포트로 Redis 장애를 재현 → TMDB 직접 호출로 대체되는지 | 1 |
| `service/CalendarServiceTest.java` | Mockito, CalendarService | 3 |
| `service/ContentServiceTest.java` | Mockito, saveContent 검증, findSavedKeys | 7 |
| `service/HighlightServiceTest.java` | Mockito, HighlightService | 4 |
| `service/KakaoSearchServiceTest.java` | Mockito, ISBN→externalId(파라미터 8개) + pickIsbn(null) | 9 |
| `service/SearchServiceTest.java` | 순수 Mockito, isSaved 일괄 처리, 페이지 정보 유지, 원본 객체 불변 | 7 |
| `service/StatisticsServiceTest.java` | Mockito, StatisticsService | 5 |
| `service/TmdbSearchServiceCacheTest.java` | `@SpringJUnitConfig` + ConcurrentMapCacheManager, 캐시 키와 unless 규칙 | 6 |
| `service/TmdbSearchServiceTest.java` | Mockito, TMDB 매핑, 타임아웃, externalId | 7 |
| `service/injectMocks.java` | **테스트가 아님.** 비어 있는 `public @interface injectMocks {}` 어노테이션 정의. 사용처 없음 | - |

- 컨트롤러 테스트(`@WebMvcTest`/MockMvc)는 **없다**. Kakao Provider의 예외 상황 테스트도 없다.

### 4-2. `./gradlew test` 실행 결과 (2026-10-19 19:03)
- **BUILD SUCCESSFUL (1분 23초). 55개 테스트, 실패 0, 에러 0, 스킵 0.**
- `compileJava`, `compileTestJava`는 UP-TO-DATE(이전 컴파일 결과 재사용)였고 `test` 태스크만 실행됐다.
- 근거: 콘솔 출력, `demo/build/test-results/test/TEST-*.xml` 11개

### 4-3. 비정상 파일과 실행상의 특이점
- 컴파일이나 실행을 막는 문제는 **발견되지 않았다**.
- `src/test/.../DemoApplication.java`: 컴파일은 된다(`build/classes/java/test/book_log/demo/DemoApplication.class` 생성). 테스트 클래스패스에서는 test 출력이 main보다 앞서므로 **test 쪽 DemoApplication이 main의 것을 가린다**. 실제로 `DemoApplicationTests` 로그에 test 쪽 문구인 "데이터 저장 테스트 시작!"이 찍혔다. test 쪽 클래스에는 `@EnableJpaAuditing`, `@EnableConfigurationProperties(ApiConfig.class)`가 없다. ApiConfig는 `@Configuration`이라 컴포넌트 스캔으로 등록된다. 결과적으로 `@SpringBootTest`는 main과 다른 설정으로 기동하고 있다.
- `src/test/.../service/injectMocks.java`: 아무 영향이 없는 빈 어노테이션이다. 컴파일된다(`injectMocks.class`).
- `DemoApplicationTests`와 `HighlightRepositoryTest`는 application.yml이 없어서 **H2 인메모리 DB**로 돌았다(로그 `jdbc:h2:mem:...`, `drop table if exists content cascade`). 운영 DB로 상정한 PostgreSQL 전용 native 쿼리(`EXTRACT`, `TO_CHAR`)는 이 테스트들에서 실행되지 않는다.
- `HighlightServiceTest.saveHighlight_Fail_TooLongText`는 1001자 텍스트로 IllegalArgumentException을 기대한다. 서비스에는 길이 검증이 없다(검증은 DTO의 `@Size`이며 컨트롤러 계층에서 동작한다). 이 테스트가 통과하는 이유는 `contentRepository.findById(1L)`를 스텁하지 않아서 Mock이 `Optional.empty()`를 반환하고, 그 결과 "존재하지 않는 콘텐츠" 예외가 나기 때문이다. 근거: `HighlightServiceTest.java:94-105`, `HighlightService.java:26`
- `TmdbSearchServiceTest.nullTitleItemTest`는 "null 제목 항목은 걸러진다"는 이름과 달리 `isNotEmpty()`만 확인한다. 서비스에도 null 제목을 거르는 로직이 없다.
- PATH의 java는 17이지만 Gradle 툴체인으로 JDK 21이 인식된다. 테스트를 실제로 실행한 JVM 버전은 로그에서 확인하지 못했다(미확인).

---

## 5. 문서와 실제 코드의 불일치

| # | 문서 (근거) | 실제 코드 |
|---|---|---|
| 1 | 요청은 루트 `CLAUDE.md`를 전제로 함 | 루트에는 없고 `.claude/CLAUDE.md`에 있음 |
| 2 | `.claude/CLAUDE.md`: "스택: Spring Boot 3.4 / Java 21 / JPA / PostgreSQL(Supabase)" | build.gradle은 3.4.1 / 21로 일치. 다만 지금 디스크에 application.yml이 없어 기본 실행 시 PostgreSQL이 아니라 H2로 뜸(테스트 로그 기준). Redis 의존성과 캐시 설정이 있지만 CLAUDE.md 스택에는 없음 |
| 3 | `.claude/decisions.md` (2026-10-19): "검색 화면 전에 백엔드에 externalId/isSaved부터 추가" | 백엔드 externalId/isSaved는 2026-10-17 커밋 3aca910, d632857로 **이미 구현**되어 있음 |
| 4 | `.claude/decisions.md`: "AniList는 @Service 주석 처리 상태라 제외" | 사실상 일치. 다만 `@Service`만이 아니라 **파일 전체(package 선언 포함)**가 주석 처리되어 있음 (`AnimeSearchService.java`) |
| 5 | `.claude/CLAUDE.md`: 완료 정리는 tasks 파일의 "발견 사항"에 적기 | tasks 폴더에는 이 파일만 있음 (규칙이고 불일치는 아님, 참고용) |
| 6 | `.github/PULL_REQUEST_TEMPLATE.md`: "UI 테스트: 검색 결과 클릭 시 폼 자동 입력 및 저장 로직 확인" | 프론트에 저장 폼이 없고 검색 결과 카드에 클릭 동작이 없음 |
| 7 | `.github/ISSUE_TEMPLATE/bug.yml` 예시: "Java 17, Spring Boot 3.x, React", `ContentForm.js:45` | Java 21 툴체인, Next.js(TS). `ContentForm` 파일은 없음 (예시 문구이므로 참고용) |
| 8 | `.github/ISSUE_TEMPLATE/feat.yml` 예시: "API 스펙 확정 및 Swagger 예시 추가", "Redis 캐시 10분" | springdoc 의존성은 있으나 `@Operation` 등 Swagger 어노테이션 사용 여부는 Grep으로 확인하지 않음(미확인). Redis 10분 TTL은 일치 (`RedisCacheConfig.java:25`) |
| 9 | `README.md`: "# book-log" 한 줄 | 설명 없음 |
| 10 | `demo/HELP.md`: Spring Boot 4.0.1 문서 링크 | build.gradle은 3.4.1 (HELP.md는 gitignore 대상인 로컬 파일) |
| 11 | `frontend/lib/api.ts` 주석: `GET /api/search?keyword=...&category=BOOK`, 반환 `UnifiedSearchResponse[]` | 백엔드는 `query`(필수)와 `page`를 받고 `SearchResponse` 객체를 반환 |
| 12 | `frontend/lib/api.ts`, `types/index.ts`: `GET /api/highlights` "(백엔드 추가 예정 엔드포인트)" | 백엔드에 없음. `/highlights` 화면이 이 API를 호출함 |
| 13 | `frontend/types/index.ts`: "UnifiedSearchResponse.java" 대응 | `externalId`, `isSaved` 누락 |
| 14 | `frontend/components/sidebar/MiniCalendar.tsx` 주석: "GET /api/calendar/activities boolean 응답", Sidebar 주석 "경량 boolean 쿼리" | 백엔드는 CalendarResponse(days 배열)를 반환하고 프론트도 그렇게 사용함. 주석만 다름 |
| 15 | `CalendarResponse.DayActivity.of(day)` 주석 "사이드바 점 찍기용 생성자" | CalendarService는 `of()`를 쓰지 않고 `new DayActivity(day, null, null)`을 직접 호출함. thumbnail/count는 항상 null이라 캘린더 화면의 썸네일·개수 배지는 표시되지 않음 |
| 16 | `next.config.js` 주석: "CORS 없이 /api/*를 Spring으로 포워딩" | `lib/api.ts`와 `search/page.tsx`가 절대 주소로 요청해 프록시를 쓰지 않음. 백엔드 CORS 허용은 StatisticsController에만 있음 |
| 17 | `origin/feat/security`의 `docs/*.md` 5개(architecture, code-guide 등) | 현재 브랜치에는 docs/ 폴더가 없음. 내용은 이번에 읽지 않음(미확인) |

---

## 내가 직접 판단해야 할 사항
1. `.claude/decisions.md`의 "검색 화면 전에 백엔드에 externalId/isSaved부터 추가"가 지금도 유효한지. 백엔드 쪽은 이미 커밋되어 있다(3aca910, d632857).
2. `application.yml`이 디스크에 없다. 그래서 `application-api-key.yml`이 로드되지 않고, 기본 실행 시 PostgreSQL이 아닌 H2로 기동된다(테스트 로그 기준). 실제 개발 실행 시 어떤 설정을 쓰는지는 코드만으로 확인할 수 없었다.
3. 로컬 `main`이 origin보다 17커밋 뒤처져 있고, 현재 작업 브랜치 `feature/frontend`는 `origin/main`과 같은 커밋이다.
4. `origin/feat/security`에 로컬에 없는 5개 커밋(JWT 인증, `SecurityConfig` 변경, docs/*.md)이 있다. 이 브랜치는 `application.yml`에 `jwt.*` 키도 추가한다.
5. `isSaved` 판별 키는 `(externalId, category)`다. 그런데 TMDB 결과의 category는 장르에 따라 MOVIE/TV/ANIME_MOVIE/ANIME_TVA로 나뉘고, 저장 시 category는 프론트가 보내는 값을 그대로 쓴다(ContentRequestDto의 category에는 `@NotNull`도 없음).
6. `SearchResponse.currentPage`를 어느 Provider도 채우지 않아 항상 0으로 응답된다. Kakao는 `page` 파라미터를 무시하고, totalPages 계산에 쓰는 페이지당 개수(20)도 코드에 고정되어 있다.
7. 프론트의 API 호출 경로가 세 갈래다: `lib/api.ts`(BASE_URL로 직접), `search/page.tsx`(localhost:8080 하드코딩), `next.config.js` rewrites(현재 미사용). 백엔드 CORS 허용은 StatisticsController에만 있다.
8. 프론트 `/highlights` 화면은 백엔드에 없는 `GET /api/highlights`를 호출한다.
9. 테스트 폴더의 `DemoApplication.java`가 main의 `@SpringBootApplication` 클래스를 가린 상태로 `@SpringBootTest`가 돌고 있다. `injectMocks.java`는 사용처 없는 빈 어노테이션이다.
10. `HighlightServiceTest.saveHighlight_Fail_TooLongText`는 의도한 이유(길이 초과)가 아니라 콘텐츠 미존재 예외 때문에 통과한다.
11. `ContentService.saveContent`의 rating null 언박싱 가능성(`ContentService.java:48`)과 `StatisticsController`의 month null 비교(`StatisticsController.java:34`). 둘 다 코드상 확인이며 실행 확인은 하지 않았다.
12. 중복 저장은 409가 아니라 400으로 응답한다. `@Valid` 실패 응답은 `ErrorResponse`가 아니라 `Map` 형태라서 에러 응답 형식이 두 가지다. 파라미터 누락이나 Enum 변환 실패는 500으로 갈 것으로 추론된다(미확인).
13. 파일 위치: 요청은 루트 `CLAUDE.md`를 전제했지만 실제 파일은 `.claude/CLAUDE.md`이며, `.claude/` 폴더 전체가 git에 추적되지 않는다.
