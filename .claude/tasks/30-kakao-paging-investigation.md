# 30. Kakao 책 검색 페이지 계산 오류 조사

- 번호: 직전이 `29`라 `30`. **조사만 했고 코드/설정/테스트는 수정하지 않았다. 커밋하지 않음.** decisions.md는 건드리지 않았다(아직 결정 전).
- 외부 API 실호출: **하지 않았다.** 키는 gitignore된 `application-api-key.yml`에 있고 출력 금지라 사용하지 않았다. 아래 Kakao 스펙은 **공식 문서(WebFetch) 기준**이며 실제 응답으로는 미확인.

## 1. Kakao 호출 코드와 요청 파라미터
- 위치: `demo/src/main/java/book_log/demo/service/KakaoSearchService.java`
  - L25 `BOOK_SEARCH_URL = https://dapi.kakao.com/v3/search/book`
  - L46-50 URI 생성: `queryParam("query", "{query}")` **하나뿐**.
  - L54 `restTemplate.exchange(uri, GET, ...)`
- 보내는 파라미터: `query`만. **`size` 안 보냄(메모 맞음). 그리고 `page`도 안 보낸다(메모에 없던 추가 발견).** `search(category, query, page)`의 `page` 인자는 메서드 안에서 한 번도 쓰이지 않는다. (TMDB는 L57에서 `page`를 보냄.)
- 호출 경로: 프론트 `searchContents` → `SearchController`(L36 `page` 기본 1) → `SearchService.search` → `KakaoSearchService.search`.

## 2. 페이지 수 계산 코드
- `KakaoSearchService.java:58-59`
  ```java
  int totalCount = body.getMeta().getTotalCount();
  int totalPages = (int) Math.ceil((double) totalCount / 20);
  ```
- 하드코딩 `20`은 이 한 곳. 분모가 `20`이고 기준이 `total_count`. `is_end`/`pageable_count`는 DTO(`KakaoBookResponse.Meta`, L29-38)에 필드로 있지만 **어디에서도 읽지 않는다**(grep).
- "/20"의 출처는 **추정**: TMDB `search/multi`가 페이지당 20건이라 그 숫자를 가져온 것으로 보임(코드 근거는 없음).

## 3. Kakao 도서 검색 API 스펙 (출처: https://developers.kakao.com/docs/ko/daum-search/dev-guide, 2026-11-11 WebFetch. 페이지 요약 결과라 원문 대조는 안 함)
| 항목 | 값 |
|---|---|
| `size` 기본값 / 범위 | **10** / 1~50 |
| `page` 기본값 / 범위 | 1 / 1~50 |
| `sort` | accuracy(기본), latest |
| `meta.total_count` | 검색된 문서 수(전체) |
| `meta.pageable_count` | 중복 문서를 제외하고, 처음부터 요청 페이지까지의 노출 가능 문서 수 |
| `meta.is_end` | 현재 페이지가 마지막 페이지인지 |
- 이론상 도달 가능 범위 = page 50 × size 50 = 2,500건(문서 수치로 계산한 값, 문서에 상한 명시는 없음).
- 미확인: `page`>50 요청 시 실제 응답(400 등), `pageable_count`가 실제로 어떤 값으로 오는지.

## 4. 종합: 어떤 입력에서 무엇이 틀리나
전제: `size`를 안 보내므로 Kakao는 **size=10**으로 응답(문서 기준). 코드는 20으로 나눔.

| total_count | 코드 계산 (L59) | size=10 기준 올바른 값 | 결과 |
|---|---|---|---|
| 10 | 1 | 1 | 일치 |
| 11 | 1 | 2 | 2페이지(1건)가 있는데 "1 / 1" |
| 100 | 5 | 10 | 절반 페이지만 노출 |
| 1,000 | 50 | 100 → 실제로는 page 상한 50 | 우연히 50 |
| 5,000 | 250 | 50(상한) | 250 / 250이 보이고, 51페이지 이상은 요청 불가(**추정**: 400) |
- 근거: 코드 L59, 문서 size 기본 10 / page 상한 50. 계산식은 직접 수치를 대입한 것(실행 안 함).
- **더 큰 문제(page 미전송, L46-50)**: 사용자가 2페이지를 눌러도 Kakao에는 page가 안 가서 **항상 1페이지(10건)가 다시 온다.** 프론트는 `page=2`를 표시하는데 결과는 1페이지와 동일. total_count=100이면 "1/5 … 5/5"로 넘겨도 같은 10건만 반복된다. (코드 근거로 확정, 실제 응답 확인은 안 함.)
- 통합 검색(ALL): `SearchService.searchAll` L116 `maxTotalPages`가 Kakao의 부풀려진/틀린 값에 영향을 받는다. Kakao는 `supports(ALL)`이 true라 ALL에서도 호출되고, 여기서도 page를 무시한다.
- 중복 제거: `total_count`는 중복 포함 수라 `pageable_count`와 다를 수 있다(문서 예시 10 vs 9).

## 5. TMDB / AniList
- **TMDB** (`TmdbSearchService.java`): 하드코딩 계산 **없음**. `page`를 L57에서 보내고 `totalPages`/`totalResults`는 응답 값(L79-80)을 그대로 쓴다. 기록만 할 점:
  - (a) `mapToResponse`가 인물을 빼고 카테고리로 **필터링한 뒤**에도 `total_pages`/`total_results`는 필터 전 값이라 페이지당 결과 수가 들쭉날쭉하고 합계가 실제와 다르다(L98-137, 코드 근거. SearchService L88에도 같은 취지 주석).
  - (b) `language=ko-KR`만 있고 size 개념 없음(multi 검색은 페이지 크기 고정, 값은 코드에서 확인 불가).
- **AniList**: `AnimeSearchService`는 전체 주석(`@Service`도 주석, decisions 2026-10-19). 주석 안에 `Page(perPage: 10)`(L32)이 하드코딩돼 있고 `page` 인자를 쿼리에 쓰는지는 활성화 전이라 판단하지 않음. **provider가 아니므로 현재 영향 없음.**
- **SearchService**: `currentPage`를 어느 곳에서도 채우지 않는다(항상 0, decisions에 기존 기록). `ANIME` 분기 L85-89는 `currentPage`를 아예 복사하지 않음(어차피 0).
- 합산: `searchAll`은 `totalResults`를 합산하고 `totalPages`는 최댓값. 서로 다른 페이지 크기(Kakao 10 / TMDB 20)의 provider를 같은 page로 호출하는 구조라, 의미가 정확하지 않다(설계 한계).

## 6. 프론트에서의 사용 (읽기만)
- `frontend/components/search/SearchResults.tsx`
  - L57 `totalPages`, L58 `totalResults`를 상태로 저장. `currentPage`는 쓰지 않고 URL `page`를 기준으로 함(L32, L139 주석).
  - L91 `총 N개` = `totalResults`(Kakao는 total_count 그대로).
  - L155 `{page} / {max(totalPages,1)} 페이지`, L158 `page < totalPages`이면 "다음" 링크 활성화.
- 영향: totalPages가 실제보다 작으면 마지막 페이지들이 막히고(total_count 11~20), 크면 존재하지 않는 페이지로 이동 가능. page 미전송과 겹쳐 책은 어느 페이지에서나 같은 결과.
- `types/index.ts` L22-27에 `currentPage/totalPages/totalResults` 정의.

## 7. 해결 방안 후보
| 안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| A | `page`와 `size`를 명시적으로 보내고(상수 `PAGE_SIZE`) `ceil(count/PAGE_SIZE)` 계산. 상한은 `min(.., 50)` | 변경이 Kakao 서비스 한 곳, 응답 구조 유지, 프론트 무변경. size를 우리가 정하므로 기본값 변경에 안전 | `total_count`는 중복 포함이라 실제 노출 수와 어긋날 수 있음 |
| B | A + 개수 기준을 `pageable_count`로 계산하고 `is_end`로 보정 | 중복 제외 반영, 실제 가능한 페이지에 더 가까움 | 문서상 `pageable_count`는 "요청 페이지까지"의 수라 값이 페이지마다 달라질 수 있어 총 페이지 계산에 쓰기 애매함(해석은 **추정**, 실호출 확인 필요) |
| C | 응답에 `hasNext`(= !is_end)를 추가하고 프론트는 번호 대신 이전/다음만 | 외부 API 의미에 가장 충실 | DTO(`SearchResponse`) 변경, 프론트 변경, TMDB/통합 검색과 의미 통일 필요 |
- **추천: A.** 이유: 오류의 직접 원인(page 미전송 + size 기본값 의존 + 하드코딩 20)이 모두 A에서 해결되고, 변경 범위가 가장 작고 기존 테스트 구조와 DTO를 유지한다. `PAGE_SIZE`를 TMDB와 맞출지는 사용자 결정 사항(20으로 보내면 현재 계산식과 맞음, 10이면 문서 기본값). B/C는 실호출로 `pageable_count`/`is_end` 동작을 확인한 뒤 후속으로 검토.
- 트레이드오프: A는 중복 문서 때문에 마지막 페이지가 비어 보일 수 있음(문서 예시 total 10 / pageable 9 근거).

## 8. 테스트 제안과 기존 테스트
**기존 테스트 중 이 계산을 제대로 검증하는 것은 없다(확인한 범위).**
- `KakaoSearchServiceFailureTest.successStillWorks`(L101-116): total_count=41 → `totalPages 3`을 단정하지만 이는 **현재 /20 동작을 고정**하는 것이라, 수정 시 같이 바뀌어야 함(옳은 값을 검증하는 게 아님).
- `KakaoSearchServiceTest`: meta.totalCount=1만 세팅(NPE 방지용), 페이지 수는 검증 안 함.
- `ExternalApiUrlEncodingTest`(Kakao): 요청 URI의 query만 검증, `page`/`size` 파라미터 검증 없음. TMDB 쪽만 page 유지를 언급.
- `SearchServiceTest`/`SearchServiceIsolationTest`: 이미 계산된 totalPages를 통과시키는 검증이라 Kakao 계산식과 무관.

제안 항목(MockRestServiceServer로 실제 URI 확인):
1. `search(BOOK, "책", 3)` → 요청 URI에 `page=3`과 `size=<상수>`가 포함. (현재는 실패해야 정상: page 없음)
2. total_count 0 → totalPages 1 (프론트가 `max(,1)`로 방어하지만 서버 규약과 일치).
3. total_count가 size의 배수 / 배수+1 경계(예: size 10일 때 10→1, 11→2, 20→2, 21→3).
4. 큰 값(total_count 5,000) → totalPages가 page 상한 50 이하(A안 채택 시).
5. size 상수를 바꿔도 요청 파라미터와 계산식이 같은 상수를 쓴다(하드코딩 재발 방지).
6. (B/C 채택 시) is_end=true면 마지막 페이지 처리.
7. 통합 검색 ALL에서 page=2일 때 Kakao 호출도 page=2로 나가는지.

## 9. 발견 사항 (기록만, 수정 안 함)
1. Kakao `page` 미전송 — 2페이지 이후 결과가 1페이지와 동일 (L46-50).
2. `is_end`, `pageable_count` 필드가 DTO에만 있고 미사용.
3. TMDB는 필터링 후 개수와 total_pages 불일치(5절 a).
4. `currentPage` 항상 0(기존 기록)이고 ANIME 분기는 복사도 안 함.
5. `searchAll`이 페이지 크기가 다른 provider를 같은 page로 합침.
6. Kakao page 상한 50 초과 시 동작은 미확인 (실호출 필요).

## 10. 정리 (노션용)
1. **문제 상황**: Kakao 책 검색에서 `total_count / 20`으로 페이지 수를 계산하는데 `size`를 보내지 않아 Kakao가 기본값 10으로 응답함. 게다가 `page`도 보내지 않아 2페이지 이후에도 1페이지 결과가 반복됨.
2. **고려한 선택지**: ① page/size 명시 + 같은 상수로 계산 ② `pageable_count`/`is_end` 기반 계산 ③ 응답에 hasNext 추가(DTO/프론트 변경).
3. **선택 이유**: ①(추천). 원인을 한 곳에서 모두 해소하고 DTO·프론트 변경이 없음. 트레이드오프: 중복 문서 때문에 마지막 페이지가 어긋날 수 있어 ②③은 실호출 확인 후 후속.
4. **막혔던 부분과 해결**: 실제 API 호출을 하지 못해(키 비공개 정책) 스펙은 문서 기준으로만 정리. 메모의 "size 미전송"만이 아니라 코드를 끝까지 읽어 page 미전송을 추가로 발견.

## 11. 복습 질문

<details>
<summary>Q1. 페이지 수 계산에서 외부 API의 기본값(size)에 의존하면 왜 위험한가? 이번 코드에서 구체적으로 어떤 입력이 틀리나?</summary>

답:

</details>

<details>
<summary>Q2. Kakao 응답의 total_count, pageable_count, is_end는 각각 무엇이고, 총 페이지 수 계산에 total_count만 쓰면 어떤 한계가 있나?</summary>

답:

</details>

<details>
<summary>Q3. (기존 코드 연결) Kakao와 TMDB 결과는 UnifiedSearchResponse/SearchResponse로 변환된다. searchAll에서 totalPages를 max로, totalResults를 합으로 합치는 구조는 provider마다 페이지 크기가 다를 때 어떤 문제가 생기나?</summary>

답:

</details>
