# 03. TMDB 검색 결과 authorOrDirector null 처리

- 브랜치: `feat/search-ui`
- 참고: `.claude/decisions.md` (2026-10-19 TMDB authorOrDirector는 null로 내려준다), `.claude/tasks/01-search-frontend.md` 발견 사항

## 목표
TMDB 검색 결과의 `authorOrDirector`를 `"상세 정보 참조"` 문자열 대신 `null`로 내려준다.
지금은 이 문자열이 저장 폼을 거쳐 그대로 DB `author_or_director`에 저장된다. 이 데이터 오염을 막는 것이 목적이다.
감독 연동(credits API)은 이번 범위 밖이다.

## 구현 요구사항
1. `TmdbSearchService` 매퍼에서 `authorOrDirector`를 null로 설정
2. 이 값에 의존하는 곳 조사 (프론트 표시, 테스트, 다른 서비스)
3. 프론트: `authorOrDirector`가 null/빈 값이면 "-"로 표시하고 화면이 깨지지 않게 한다.
   저장 폼에서도 null이면 요청 본문에서 해당 필드를 보내지 않는다.
4. 기존 `TmdbSearchServiceTest`가 문자열을 검증하고 있으면 null 기준으로 수정하고, null 케이스 테스트를 추가한다
5. Redis 캐시에 "상세 정보 참조"가 들어간 응답이 남아 있을 수 있는지 확인해서 보고한다
   (캐시되는 데이터가 매핑 전인지 후인지 코드로 확인)

## 수정 허용 범위
`TmdbSearchService`, `TmdbSearchServiceTest`, 프론트 search 관련 컴포넌트(`frontend/components/search/*`)만.

## 범위 밖
Kakao/AniList 매퍼, credits API 연동, 이미 저장된 DB 데이터 정리, 캘린더, 인증

---

## 구현 내용
- `TmdbSearchService.mapToResponse`: `.authorOrDirector("상세 정보 참조")` → `.authorOrDirector(null)`. `search/multi` 응답에는 감독 정보가 없어서 "값 없음"을 null로 표현한다.
- `TmdbSearchServiceTest.authorOrDirectorIsNull` 추가: movie 1개와 tv 1개를 넣고 두 결과의 `authorOrDirector`가 모두 null인지 확인한다(`containsOnlyNulls`). 기존 테스트 7개 중 이 문자열을 검증하는 것은 없었으므로 수정한 기존 테스트는 없다.
  - 수정 전 코드로 돌리면 이 테스트가 실패하는 것을 확인했다. 서비스 파일을 HEAD 버전으로 임시 교체해 실행한 뒤 복원했다.
- `SearchResultCard.tsx`: 예전에는 `authorOrDirector &&`로 줄 전체를 숨겼다. 이제 `authorOrDirector?.trim() || '-'`로 항상 `저자/감독 · -`을 표시한다. 그래서 카드마다 줄 수가 달라지지 않는다.
- `SaveContentForm.tsx`: `const author = item.authorOrDirector?.trim() || null` 기준 하나를 표시와 전송에 함께 쓴다.
  - 표시: `저자/감독 · {author ?? '-'}`
  - 전송: `author`가 있을 때만 `authorOrDirector` 키를 넣는다. null, 빈 문자열, 공백뿐인 값은 키 자체를 보내지 않는다. 이전 코드도 falsy면 보내지 않았지만, 공백뿐인 값은 걸러내지 못했다.

## 조사 결과 (authorOrDirector 의존 여부)
| 대상 | 결과 |
|---|---|
| `SearchService` | 결과를 합치고 정렬(`voteAverage`)하며 `isSaved`(`externalId`+`category`)를 채운다. `authorOrDirector`는 읽지 않는다. `toBuilder()`로 복사할 때도 null이 그대로 유지된다 |
| `UnifiedSearchResponse` | `String` 필드라 null을 담을 수 있다. `@JsonInclude(NON_NULL)` 설정이 없어서 JSON에는 `"authorOrDirector": null`로 나간다(코드 기준) |
| `ContentRequestDto` / `Content` | `authorOrDirector`에 `@NotBlank` 같은 검증이 없다. 키를 생략하면 null로 바인딩된다. 엔티티 컬럼에도 `nullable=false`가 없다. 따라서 저장은 통과하고 NULL로 들어갈 것이다(DB 실제 제약은 미확인) |
| `ContentResponseDto` | `author = entity.getAuthorOrDirector()`. null을 그대로 전달한다 |
| `KakaoSearchService` | 자체적으로 저자 문자열을 만든다. TMDB 변경과는 무관하다(범위 밖이라 보지 않음) |
| 테스트 | `TmdbSearchServiceTest`, `TmdbSearchServiceCacheTest`, `SearchServiceTest` 모두 "상세 정보 참조"를 검증하지 않는다. `HighlightRepositoryTest`의 `"은서"`는 무관하다 |
| frontend | `authorOrDirector`를 쓰는 곳은 `SearchResultCard`, `SaveContentForm`, `types/index.ts` 세 곳뿐이다. 캘린더나 통계 화면에서는 쓰지 않는다 |

## Redis 캐시 확인 결과
- **캐시되는 데이터는 매핑 후 값이다.** `@Cacheable`은 `TmdbSearchService.search()` 메서드에 붙어 있고, 이 메서드의 반환값을 캐싱한다. 반환값은 `mapToResponse()`를 거친 `SearchResponse`다. 그래서 배포 전에 쌓인 항목에는 `"authorOrDirector": "상세 정보 참조"`가 이미 들어가 있다.
- **배포 직후 최대 10분 동안은 옛 응답이 나갈 수 있다.** 캐시 키는 `검색어:카테고리:페이지`다. 키에 버전 같은 구분값이 없으므로, 같은 검색어로 검색하면 옛 항목이 그대로 조회된다. 값은 `GenericJackson2JsonRedisSerializer`로 저장된다. DTO 필드 구조가 바뀌지 않아서 역직렬화 오류도 나지 않는다. 즉, 옛 값이 실패 없이 조용히 응답으로 나간다.
- **이 시간 동안 저장하면 문자열이 DB에 들어갈 수 있다.** 프론트는 "상세 정보 참조"를 정상 값으로 보고 표시하고 전송한다.
- 대응(이번에 하지 않음): ① 배포 후 10분이 지나 자연 만료되기를 기다린다 ② 배포 직후 `tmdbSearch::*` 키를 삭제한다 ③ 응답 구조나 의미가 바뀔 때 `cacheNames`를 바꿔 캐시 버전을 올린다. 현재 TTL이 짧아서 ①로 충분하다고 본다.
- 이 판단은 코드 기준이다. 실제 Redis에 남은 키는 확인하지 않았다(서버 필요).

## 검증 결과
### 서버 없이 확인한 것
- [x] `./gradlew test`: **58개 전부 통과**(실패 0, 에러 0, 스킵 0). 기존 57개 + 새 테스트 1개. `TmdbSearchServiceTest`는 8개
- [x] 새 테스트가 수정 전 코드에서 실패하는 것 확인(서비스 파일을 임시로 HEAD 버전으로 교체한 뒤 복원)
- [x] 프론트 `npx tsc --noEmit` 통과. 타입이 `string`이어도 `?.trim()`은 컴파일된다
- [ ] ESLint: 프로젝트에 ESLint 설정 파일이 없어서 실행하지 못했다
- [x] 캐시가 매핑 후 값을 저장하는지: `@Cacheable` 위치와 반환값 기준으로 코드에서 확인

### 서버가 필요한 것 (시나리오)
1. **TMDB 검색 결과의 저자/감독 칸이 "-"로 보이는지**
   - 캐시를 피하려면 배포 후 처음 검색하는 검색어를 쓰거나 10분을 기다린다. Redis에서 `tmdbSearch::*`를 지우는 방법도 있다.
   - 검색 화면에서 카테고리 '영화'로 예: "파이트 클럽"을 검색한다.
   - 기대 결과: 개발자 도구 Network에서 `/api/search` 응답의 `authorOrDirector`가 `null`이다. 카드에는 `저자/감독 · -`가 보인다. 카드 높이나 줄 간격이 깨지지 않는다.
   - 대조 확인: '도서'로 검색하면 실제 저자 이름이 그대로 보이는지(Kakao 결과에 영향이 없는지) 확인한다.
2. **저장 후 DB의 author_or_director가 NULL인지**
   - 위 TMDB 결과에서 '기록하기'를 누르면 모달에 `저자/감독 · -`가 보인다. 그 상태로 저장한다.
   - Network 탭에서 `POST /api/contents` 요청 본문에 `authorOrDirector` 키가 **없는지** 확인한다. 응답은 `201`이어야 한다.
   - DB: `SELECT id, title, author_or_director FROM content ORDER BY id DESC LIMIT 1;` → `author_or_director`가 `NULL`이어야 한다. 문자열 `'-'`나 `'상세 정보 참조'`가 들어가 있으면 안 된다.

## 변경 파일 (커밋하지 않음)
- 수정: `demo/src/main/java/book_log/demo/service/TmdbSearchService.java`
- 수정: `demo/src/test/java/book_log/demo/service/TmdbSearchServiceTest.java`
- 수정: `frontend/components/search/SearchResultCard.tsx`
- 수정: `frontend/components/search/SaveContentForm.tsx`
- 추가(작업 기록): `.claude/tasks/03-tmdb-author-null.md`

## 발견 사항 (수정하지 않음)
1. **프론트 타입이 실제 응답과 다르다.** `types/index.ts:14`는 `authorOrDirector: string`이지만 TMDB 결과는 이제 null이다. `string | null`로 바꿔야 한다. 수정 허용 범위(search 컴포넌트)를 벗어나서 그대로 두었다. 지금은 컴포넌트에서 `?.`로 방어하고 있어 동작에는 문제가 없다.
2. **DTO 주석이 낡았다.** `UnifiedSearchResponse.java:15`의 주석 `영화: 상세페이지 참조(또는 빈값)`가 현재 동작(null)과 맞지 않는다.
3. **이미 저장된 오염 데이터가 남아 있다.** 그동안 TMDB 결과를 저장한 행에는 `author_or_director = '상세 정보 참조'`가 들어 있을 것이다. 정리가 필요하면 `UPDATE content SET author_or_director = NULL WHERE author_or_director = '상세 정보 참조';`를 검토한다(범위 밖).
4. **캐시 키에 버전 구분이 없다.** 응답의 의미가 바뀌는 배포 때마다 옛 캐시가 최대 TTL만큼 섞여 나간다. 지금은 TTL이 10분이라 영향이 작지만, TTL을 늘리면 cacheNames 버전 관리나 배포 시 캐시 삭제 절차가 필요해진다.

---

## 정리 (트러블슈팅 기록용)
1. **문제 상황**
   TMDB `search/multi`에는 감독 정보가 없다. 그래서 매퍼가 안내 문구 `"상세 정보 참조"`를 `authorOrDirector`에 넣었다. 프론트는 이 값을 진짜 감독 이름처럼 저장 요청에 담았고, 그 결과 DB에 의미 없는 문자열이 쌓였다(데이터 오염).
2. **고려한 선택지**
   - A. 서버가 null을 내리고, 화면 표시("-")는 프론트가 결정한다
   - B. 서버가 `"-"`나 빈 문자열 `""`을 내린다
   - C. 문자열은 유지하고, 프론트가 저장할 때 `"상세 정보 참조"`를 걸러낸다
3. **선택 이유 (트레이드오프)**
   A를 골랐다. B는 프론트 수정이 거의 없지만 표시용 값이 데이터 자리에 들어간다는 점이 같다. `"-"`도 결국 DB에 저장된다. C는 서버 응답이 계속 거짓 데이터를 담는다. 매직 문자열을 프론트가 알아야 하고, 문구가 바뀌면 깨진다. A는 "값 없음"을 null 하나로 표현한다. 그래서 프론트 표시, 요청 생략, DB NULL이 같은 기준으로 이어진다. 비용은 프론트에 null 처리 코드가 늘어나는 것이다.
4. **막혔던 부분과 해결**
   - 서버를 고쳐도 Redis에는 **매핑이 끝난 응답**이 캐시되어 있다. 그래서 배포 직후 최대 10분 동안은 옛 문자열이 나갈 수 있다는 것을 `@Cacheable` 위치를 보고 확인했다. TTL이 짧아 자연 만료로 충분하다고 판단하고 보고만 했다.
   - 화면에서 null이면 줄을 숨기던 방식(`&&`)은 깨지지는 않았지만, 카드마다 높이가 달라졌다. 요구사항대로 항상 "-"를 표시하도록 바꿨다. 저장 폼은 표시와 전송이 같은 기준(`trim() || null`)을 쓰도록 변수 하나로 묶었다.

> 개념 메모
> - **캐시는 "어느 단계의 값"을 저장하는지가 중요하다**: `@Cacheable`은 메서드의 반환값을 저장한다. 변환(매핑)이 메서드 안에 있으면 변환 결과가 캐시된다. 그래서 변환 로직을 바꿔도 이미 캐시된 값은 TTL이 끝날 때까지 바뀌지 않는다. 원본 응답을 캐시하고 변환을 바깥에서 하면 이 문제는 없다. 대신 요청마다 변환 비용이 든다.
> - **null vs 표시용 문자열**: "값 없음"을 `"-"`나 안내 문구로 채우면 화면은 편하지만, 그 값이 저장·집계·검색에 진짜 데이터처럼 섞인다. 데이터는 null로 두고 표시 방식은 화면 계층이 정한다(표현과 데이터의 분리).

## 복습 질문
<details>
<summary>1. 서버를 배포한 직후에도 검색 결과에 "상세 정보 참조"가 보일 수 있는 이유는? <code>@Cacheable</code>이 붙은 위치와 캐시 키 구성을 기준으로 설명해보라.</summary>

`@Cacheable`은 `TmdbSearchService.search()`의 반환값, 즉 `mapToResponse()`가 끝난 `SearchResponse`를 저장한다. 캐시 키는 `검색어:카테고리:페이지`로, 배포 전후를 구분하는 값이 없다. 그래서 배포 전에 같은 키로 저장된 항목이 TTL(10분)이 끝날 때까지 그대로 조회된다. DTO 구조가 같아서 역직렬화 오류도 나지 않으므로 옛 값이 조용히 나간다. 대응 방법은 자연 만료를 기다리거나, `tmdbSearch::*` 키를 삭제하거나, cacheNames 버전을 올리는 것이다.
</details>

<details>
<summary>2. 서버가 null 대신 "-"를 내려줬다면 무엇이 문제인가? 저장 흐름(검색 → 저장 폼 → DB)을 따라가며 설명해보라.</summary>

프론트는 `"-"`를 truthy한 정상 값으로 본다. 그래서 `authorOrDirector: "-"`를 요청 본문에 담고, DB에 `'-'`가 저장된다. 원래 문제(표시용 문자열로 인한 데이터 오염)가 문자열만 바뀐 채 그대로 남는다. null로 내려주면 프론트가 키를 생략하고, DTO는 null로 바인딩되며, DB에는 NULL이 저장된다. "값 없음"이 끝까지 같은 의미로 유지된다.
</details>

<details>
<summary>3. (기존 코드 연결) 검색 결과에 붙는 <code>isSaved</code>는 왜 캐시에 남은 옛 값 문제가 생기지 않는가? <code>SearchService.markSavedStatus</code>와 <code>toBuilder()</code>를 기준으로, authorOrDirector와 어떻게 다른지 비교해보라.</summary>

`isSaved`는 사용자 DB 상태라서 캐시 바깥인 `SearchService.search()`에서 요청마다 IN 쿼리로 새로 채운다(decisions 2026-10-19). 이때 캐시된 객체를 직접 바꾸지 않고 `toBuilder()`로 복사본을 만들어 `isSaved`만 바꾼다. 그래서 캐시 내용은 오염되지 않고, 값은 항상 최신이다. 반면 `authorOrDirector`는 캐시 대상 구간(Provider의 `search()` 안)에서 만들어지므로, 매핑 로직을 바꿔도 TTL 동안 옛 값이 남는다. 캐시 안에서 만들어지는 값인지, 바깥에서 만들어지는 값인지에 따라 달라진다.
</details>
