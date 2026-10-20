# 01. 검색 결과 화면 + 콘텐츠 저장 폼 (frontend)

- 브랜치: `feat/search-ui` (main 5030134에서 분기)
- 참고: `.claude/tasks/00-inventory.md`

## 목표
frontend/ 에 검색 결과 화면과 콘텐츠 저장 폼을 완성한다.
- Kakao/TMDB 통합 검색 결과 표시
- 결과에서 콘텐츠를 선택해 저장하는 폼 (viewDate, rating, status, comment)
- 이미 저장된 콘텐츠는 '저장됨' 표시 (검색 응답의 isSaved 사용)

## 백엔드 변경 허용 범위
- `UnifiedSearchResponse` JSON 키(`isSaved`, `externalId`) 검증 테스트 1개 추가만 허용. 그 외 demo/ 수정 금지.

## 범위 밖
하이라이트 화면 및 GET /api/highlights, 캘린더, 통계, 인증(feat/security), AniList,
test 폴더의 DemoApplication.java, HighlightServiceTest

---

## 0단계 결과 (pull 후 재확인)
- `main`을 `origin/main`으로 fast-forward(2b8fe0f → 5030134)한 뒤 `feat/search-ui` 브랜치를 만들었다. 추적 파일 변경은 없었고, 미추적 파일은 `.claude/`뿐이었다.
- pull 후 HEAD(5030134)는 인벤토리를 조사한 `feature/frontend`와 **같은 커밋**이다(`git diff feature/frontend HEAD` 결과 없음). 따라서 00-inventory.md 대비 **달라진 점은 없다**.
  - frontend 구조와 스타일: 인벤토리 3장과 같음
  - UnifiedSearchResponse JSON 키: `isSaved`, `externalId` (`"saved"` 아님). 새 테스트 `UnifiedSearchResponseJsonTest`로 확인
  - `ContentService.saveContent`의 rating: `requestDto.getRating() < 0`에서 `Double`을 언박싱한다. rating이 null이면 NPE가 나고, `GlobalExceptionHandler`의 `Exception` 핸들러가 500 "서버 내부 오류가 발생했습니다..."로 응답한다(`ContentService.java:48`). 코드 기준 판단이며 서버 실행 확인은 하지 않았다.

## 기존 프론트 스타일 요약 (따른 규칙)
1. Next.js 15 App Router + TypeScript. 페이지는 `app/<route>/page.tsx`에서 `components/<기능>/PascalCase.tsx`를 렌더링한다. 상호작용이 있으면 `'use client'`를 쓰고 `export default function`으로 내보낸다. 세미콜론 없이 작은따옴표를 쓴다(다수 파일 기준).
2. 스타일은 Tailwind 유틸리티만 쓴다. 베이스는 neutral, 포인트는 violet이다. 카드는 `bg-white dark:bg-neutral-950 border border-neutral-200 dark:border-neutral-800 rounded-2xl p-5`이다.
3. 카테고리 배지는 `lib/constants.ts`의 `CATEGORY_BADGE`와 `CATEGORY_LABEL`(`rounded-full text-[11px]`)을 쓰고, 아이콘은 lucide-react를 쓴다. 주 버튼은 `bg-violet-600 text-white rounded-lg`, 보조 버튼은 `border border-neutral-300 bg-white rounded-lg`이다.
4. 입력창은 `bg-neutral-100 border rounded-xl focus:ring-2 focus:ring-violet-500`이다. 로딩은 `animate-pulse` 스켈레톤, 빈 상태는 `py-20 text-center text-neutral-400 text-sm`이다.
5. API 호출은 `lib/api.ts`의 `request<T>()`에 모으고, 타입은 `types/index.ts`에 백엔드 DTO와 1:1로 둔다.

### 기존과 다르게 만든 부분과 이유
- **검색 페이지를 서버 컴포넌트에서 클라이언트 컴포넌트로 바꿨다.** 늦게 도착한 응답을 무시하는 처리와, 저장 성공 시 재검색 없이 '저장됨'으로 바꾸는 처리는 브라우저 쪽 상태가 있어야 한다. URL(`/search?category&query&page`)을 기준으로 하는 방식은 그대로 유지했다.
- **검색 결과 카드 모양을 바꿨다.** 기존 `rounded-lg shadow-sm`에서 `rounded-2xl border`로 바꿨다. 다른 화면(HighlightCard, 캘린더)의 카드 톤에 맞추기 위해서다.
- **카테고리 배지를 공용 상수로 바꿨다.** 검색 페이지 안의 라벨 함수와 단일 violet 배지 대신 `CATEGORY_LABEL`, `CATEGORY_BADGE`를 쓴다. 그래서 라벨이 일부 바뀐다(도서→책, TV 프로그램→드라마, 극장판→애니 극장판, TVA→애니 TV).
- **검색과 저장 API만 `next.config.js`의 rewrites 프록시(같은 출처 `/api/*`)로 호출한다.** 브라우저에서 `localhost:8080`으로 직접 POST하면 CORS에 막힌다. 백엔드 `@CrossOrigin`은 StatisticsController에만 있고, 백엔드는 이번 범위에서 수정 금지다. `request()`에 `baseUrl` 인자를 추가했고, 기존 함수(캘린더, 통계, 하이라이트)의 호출 주소는 바꾸지 않았다.
- **`request()` 실패 시 `ApiError(status, body)`를 던지도록 바꿨다.** 서버의 400 메시지를 폼에 보여주려면 응답 본문이 필요하다. 에러 메시지 문자열은 기존과 같은 `API Error {status}: {path}`라서, 기존 호출부(catch만 함)에는 영향이 없다.
- **평점은 숫자 입력 대신 select(평점 없음 / 0~5, 0.5 단위)로 만들었다.** 0.5 단위가 아닌 값이 들어갈 수 없게 하기 위해서다.
- **viewDate 기본값은 오늘이다.** 입력에 `max=오늘`을 걸었고, 제출할 때 다시 검사한다. 날짜는 사용자 로컬 기준으로 계산한다.
- **externalId가 없는 결과는 '기록하기'를 비활성화했다.** 이런 항목은 isSaved가 항상 false라 '저장됨'을 표시할 수 없다. 또 null externalId로 저장하면 같은 카테고리의 다른 null 항목까지 중복으로 판정될 가능성이 있다(미확인). 요구사항에 없는 선택이므로 판단이 필요하다.

## API 계약과 실제 코드의 차이 (코드를 따름)
| 항목 | 요청서의 계약 | 실제 코드 |
|---|---|---|
| 검색 응답 | 배열 | `SearchResponse` 객체 `{ results[], currentPage, totalPages, totalResults }` (`SearchController.java:31`). `currentPage`는 항상 0 |
| 검색 파라미터 | category, query | `category` **필수**(Enum), `query` 필수, `page` 기본값 1 |
| rating 생략 | 생략 가능 | 요청서대로 생략해서 보내도록 구현했다. 하지만 **현재 백엔드는 rating이 없으면 500**으로 응답한다(위 0단계 참고). 그래서 '평점 없음'으로 저장하면 폼에 "서버 내부 오류가 발생했습니다..."가 표시될 것으로 예상된다(미확인) |
| genre | 본문 필드 | 검색 응답에 genre가 없어서 보내지 않는다 |
| authorOrDirector | 자동 입력 | TMDB 결과는 항상 `"상세 정보 참조"`라는 문자열이고, 이 값이 그대로 저장된다 |
| 미래 날짜 | - | 서버는 서버 시간대의 `LocalDate.now()`로 검사하고, 프론트는 사용자 로컬 날짜로 검사한다 |

## 검색 캐시 동작 추적 (읽기만 함)
1. **캐시 종류와 설정 위치**: Spring Cache 추상화(`@EnableCaching`)와 Redis를 쓴다.
   - `config/RedisCacheConfig.java`: `RedisCacheManager`, TTL 10분, null 캐싱 안 함, key는 String, value는 `GenericJackson2JsonRedisSerializer`로 직렬화
   - `config/CacheErrorConfig.java`: `LoggingCacheErrorHandler`. Redis 장애가 나면 캐시 미스로 처리한다
   - 캐시 대상은 `TmdbSearchService.search` 하나뿐이다(`@Cacheable(cacheNames="tmdbSearch")`). Kakao 검색은 캐시하지 않는다.
2. **캐시 키**: `#query + ':' + #category + ':' + #page` (`TmdbSearchService.java:39-41`). 예: `엘리멘탈:MOVIE:1`
   - 여기서 category는 SearchService가 Provider에 넘기는 값이다. ALL 검색이면 `ALL`, ANIME 검색이면 `ANIME`이 들어간다.
   - Redis 실제 키는 RedisCacheManager 기본 접두사를 붙여 `tmdbSearch::엘리멘탈:MOVIE:1` 형태일 것으로 보인다(미확인).
   - 결과가 비어 있으면 캐싱하지 않는다(`unless`).
3. **저장 시점**: **isSaved를 채우기 전** 데이터가 저장된다.
   - `SearchService.search()`는 `searchProviders()`에서 Provider 프록시를 호출하고, 이때 캐시를 거친다. TMDB의 `mapToResponse`는 isSaved를 설정하지 않으므로 null이다.
   - 그다음 `markSavedStatus()`가 캐시 바깥에서 `toBuilder()`로 복사본을 만들어 isSaved를 채운다. 원본 객체는 바뀌지 않는다(`SearchService.java:26-59`, `SearchServiceTest.doesNotMutateOriginalObjects`).
   - Provider는 `List<SearchProvider>`로 주입된 스프링 빈(프록시)이라 자기 호출 문제가 없다.
4. **저장 시 무효화**: **하지 않는다.** `@CacheEvict`, `@CachePut`, `cacheManager.evict`가 main 코드에 없다(Grep 결과). RedisCacheConfig 주석에도 "@CacheEvict가 필요 없다"고 명시되어 있다.
5. **판단**: "저장 직후 같은 검색어로 재검색하면 '저장됨'이 뜬다"는 **코드상 성립한다.**
   - 근거: 캐시에는 isSaved가 없고, isSaved는 매 요청마다 `ContentRepository.findByExternalIdIn`(IN 쿼리)으로 DB를 다시 조회해 채운다. 저장(`@Transactional saveContent`)은 POST 응답 전에 커밋된다. 그래서 다음 검색이 캐시 히트여도 새 행이 조회된다.
   - 저장할 때 프론트는 검색 결과의 `category`와 `externalId`를 그대로 보낸다. 따라서 판별 키 `(externalId, category)`가 검색 결과와 일치한다.
   - 이번 프론트 구현은 저장 성공 직후 재검색 없이 화면 상태만 '저장됨'으로 바꾼다. 재검색했을 때의 표시는 위 서버 로직에 따른다.
   - **미확인**: 실제 Redis 연결, Redis에서 `SearchResponse`를 역직렬화할 때 isSaved/externalId가 보존되는지, 실제 HTTP 응답 키, 저장 직후 재검색 결과. 모두 서버를 띄워야 확인할 수 있다.

## 검증 결과
### 서버 없이 확인한 것
- [x] 백엔드 직렬화 테스트 `UnifiedSearchResponseJsonTest`: 통과. 키는 `isSaved`, `externalId`이고 `saved` 없음
- [x] `./gradlew test` 전체: 56개 통과(실패 0, 에러 0, 스킵 0) = 기존 55개 + 새 테스트 1개
- [x] `npx tsc --noEmit`: 에러 0
- [x] `npm run build`(next build): 성공. 라우트 5개 정적 생성, `/search` 5.38 kB
- [ ] 린트: **확인 못 함.** 프로젝트에 ESLint 설정과 의존성이 없다. 설치는 범위 밖이라 진행하지 않았다.

### 서버가 필요해서 못 본 시나리오 (체크리스트)
- [ ] 헤더에서 카테고리와 검색어로 검색했을 때 결과 카드(썸네일, 제목, 저자/감독, 출시일, 평점, 카테고리)가 정상 표시되는지
- [ ] rewrites 프록시(`/api/search`, `/api/contents`)가 `localhost:8080`으로 전달되는지(`NEXT_PUBLIC_API_URL` 미설정 시 기본값)
- [ ] 로딩 스켈레톤, 빈 결과("검색 결과가 없어요"), 백엔드가 꺼져 있을 때 에러와 '다시 시도'가 동작하는지
- [ ] 빠르게 검색어나 페이지를 연달아 바꿨을 때 이전 응답이 최신 결과를 덮어쓰지 않는지(요청 취소 + 요청 번호 비교)
- [ ] DB에 이미 저장된 항목이 '저장됨' 배지로 표시되고 '기록하기'가 비활성화되는지
- [ ] 저장 성공(201) 시 모달이 닫히고 해당 카드가 재검색 없이 '저장됨'으로 바뀌는지
- [ ] 저장 직후 같은 검색어로 재검색했을 때 '저장됨'이 바로 뜨는지(TMDB 캐시 히트 상황 포함)
- [ ] 다른 탭에서 저장된 항목을 중복 저장했을 때 "이미 저장된 콘텐츠입니다."가 폼에 표시되는지
- [ ] 평점을 '평점 없음'으로 두고 저장했을 때 실제 응답(코드상 500으로 예상)과 폼에 표시되는 문구
- [ ] 제목이 null인 TMDB 항목을 저장했을 때 `@NotBlank` 필드 에러 맵 메시지("제목은 필수입니다.")가 표시되는지
- [ ] 서버와 브라우저의 시간대가 다를 때 오늘 날짜 저장이 "미래 날짜"로 거절되지 않는지
- [ ] 한줄평 500자 카운터와 입력 제한, 다크 모드 표시
- [ ] Redis가 떠 있을 때 캐시 히트 응답에서도 isSaved와 externalId가 정상인지

## 변경 파일 (커밋하지 않음)
- 수정: `frontend/app/search/page.tsx`, `frontend/lib/api.ts`, `frontend/types/index.ts`
- 추가: `frontend/components/search/SearchResults.tsx`, `frontend/components/search/SearchResultCard.tsx`, `frontend/components/search/SaveContentForm.tsx`
- 추가(백엔드 테스트, 허용 범위): `demo/src/test/java/book_log/demo/dto/response/UnifiedSearchResponseJsonTest.java`
- 추가(작업 기록): `.claude/tasks/01-search-frontend.md`
- 그 외 demo/의 main 코드와 설정은 변경하지 않았다. `tsc`가 만든 `frontend/tsconfig.tsbuildinfo`는 삭제했다.

## 발견 사항 (수정하지 않음)
- rating이 null이면 백엔드에서 NPE가 나고 500으로 응답한다(`ContentService.java:48`). 프론트 요구사항("비우면 rating을 보내지 않음")과 충돌한다.
- TMDB 결과의 `authorOrDirector`는 항상 "상세 정보 참조"이고, 이 값이 그대로 DB에 저장된다.
- 헤더 `SearchForm`은 페이지 이동 후 URL의 검색어나 카테고리를 반영하지 않는다(기존 동작, 이번에 수정 안 함).
- `SearchResponse.currentPage`는 항상 0이다. 그래서 프론트는 URL의 page를 기준으로 페이지를 표시한다.

---

## 정리 (트러블슈팅 기록용)
1. **문제 상황**
   검색 결과에서 바로 기록을 남기고, 이미 저장한 작품은 '저장됨'으로 보여줘야 했다. 기존 검색 페이지는 서버 컴포넌트에서 한 번 fetch하는 구조였다. 그래서 응답 순서 꼬임을 막을 수 없었고, 저장 후 화면만 갱신할 방법도 없었다. 브라우저에서 백엔드로 직접 POST하면 CORS에 막히는 구조이기도 했다.
2. **고려한 선택지**
   - A. 서버 컴포넌트를 유지하고 저장 후 `router.refresh()`로 재검색
   - B. 클라이언트 컴포넌트로 전환하고, AbortController와 요청 번호로 경쟁 상태를 막고, 저장 성공 시 로컬 상태만 갱신
   - C. 상태관리/데이터 패칭 라이브러리(SWR, React Query) 도입
3. **선택 이유 (트레이드오프)**
   B를 골랐다. 요구사항이 "재검색 없이 갱신"이라 A는 맞지 않는다. C는 의존성 설치가 범위 밖이다. B는 코드가 조금 늘지만 의존성 없이 요구를 모두 만족한다. CORS는 백엔드를 수정할 수 없어서, 이미 있던 Next rewrites 프록시를 검색과 저장에만 적용했다. 대신 이 두 API는 Next 서버(dev/start)를 통해서만 호출된다.
4. **막혔던 부분과 해결**
   - 요청서의 계약은 "검색 응답 = 배열"이었지만 실제 코드는 `SearchResponse` 객체였다. 코드를 따랐다.
   - "rating 생략"은 현재 백엔드에서 NPE(500)를 일으킨다. 백엔드 수정이 금지라서 요청서대로 구현하고 발견 사항으로 남겼다.
   - `useSearchParams`를 쓰는 클라이언트 컴포넌트는 Next 15 정적 빌드에서 Suspense 경계가 필요하다. `page.tsx`에서 Suspense로 감쌌다.

> 개념 메모
> - **경쟁 상태(race condition)**: 요청 A 다음에 B를 보냈는데 A가 늦게 오면 화면이 A 결과로 덮인다. 그래서 이전 요청은 `AbortController`로 취소하고, 도착한 응답의 요청 번호가 최신이 아니면 버린다. 취소가 늦는 경우까지 대비해 두 장치를 함께 쓴다.
> - **캐시와 isSaved 분리**: 캐시에는 모두에게 같은 외부 데이터(TMDB 결과)만 넣는다. 사용자별로 바뀌는 상태(isSaved)는 캐시를 꺼낸 뒤 매번 IN 쿼리 한 번으로 채운다. 그래서 저장 직후에도 캐시를 지울 필요가 없다.

## 복습 질문
1. 검색어를 빠르게 바꿀 때 `AbortController`만 쓰지 않고 요청 번호(`latestRequestId`) 비교를 함께 쓰는 이유는 무엇인가?
2. rating을 비웠을 때 요청 본문에서 키를 빼면 현재 백엔드에서 어떤 일이 생기며, 그 원인은 Java의 어떤 동작(언박싱)인가?
3. (기존 코드 연결) TMDB 검색 결과가 Redis에 10분간 캐시되는데도 저장 직후 재검색에서 '저장됨'이 바로 뜨는 이유를 `SearchService.markSavedStatus`와 `findByExternalIdIn` 기준으로 설명해보라.
