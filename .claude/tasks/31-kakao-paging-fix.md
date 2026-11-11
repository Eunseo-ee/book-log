# 31. Kakao 책 검색 페이지네이션 수정 (task 30 추천안 A)

- 번호: 직전이 `30`이라 `31`. 커밋하지 않음.
- 범위: `KakaoSearchService.java` 한 곳 + 신규 테스트 1개. TMDB, AniList, UnifiedSearchResponse/SearchResponse, 프론트, DTO, 기존 테스트는 수정하지 않았다.

## 1. 변경 내용
`demo/src/main/java/book_log/demo/service/KakaoSearchService.java`
- 상수 `PAGE_SIZE = 20`, `MAX_PAGE = 50` (package-private, 테스트에서 참조).
- 요청 URI에 `page`(받은 값 그대로)와 `size`(PAGE_SIZE) 추가. 기존 `query` 인코딩 방식은 그대로.
- 계산식: `Math.min((totalCount + PAGE_SIZE - 1) / PAGE_SIZE, MAX_PAGE)`. `double` 나눗셈 + `Math.ceil` 대신 정수 올림 나눗셈(`(a + b - 1) / b`)이라 실수 변환이 없고 PAGE_SIZE와 같은 상수를 쓴다. `total_count=0`이면 0 (기존 `ceil(0/20)=0`과 같음, 프론트는 `max(totalPages,1)`로 표시).
- `totalResults`는 기존대로 Kakao `total_count` 그대로(표시용 "총 N개").
- page 처리:
  - `page > 50`: **Kakao를 호출하지 않고** 빈 results, `totalPages=50`, `totalResults=0` 반환.
  - `page < 1`: 1로 보정해서 전송 (컨트롤러 기본값이 1이라 정상 경로에선 안 일어남).

## 2. 상한(50) 초과 동작과 근거
- 후보: ① 50으로 줄여 호출 ② 그대로 호출 ③ 호출하지 않고 빈 결과.
- ①은 51페이지를 요청해도 50페이지 내용이 다시 나와 "page 미전송 때 같은 결과 반복"과 같은 종류의 오해를 만든다. ②는 Kakao가 어떻게 응답하는지 **미확인**(400 추정)이고, 그러면 기존 catch가 `totalPages 1`로 덮어써 상한 정보가 사라진다. → ③ 채택. 도달 가능한 페이지 수(50)를 알려 주고 불필요한 외부 호출을 막는다.
- 트레이드오프: 프론트는 "검색 결과가 없어요"를 보여 준다(URL을 직접 고쳐 page=51을 넣는 경우에만 발생. 정상 UI는 `page < totalPages`(최대 50)에서만 "다음"이 활성화되어 51에 가지 않음).
- 결과적으로 사용자가 볼 수 있는 책은 최대 50×20 = 1,000건. `total_count`가 더 커도 "총 N개"는 그대로 크게 보이므로 표시와 접근 가능 범위가 다르다(후속 항목 3).
- page 상한 50/size 범위 1~50은 공식 문서 요약 기준이며 **원문 대조와 실호출은 미확인**.

## 3. 테스트
신규 `demo/src/test/java/book_log/demo/service/KakaoSearchServicePagingTest.java` (진짜 RestTemplate + MockRestServiceServer):
- totalPages 경계: total_count 0→0, 1→1, 20→1, 21→2, 40→2, 41→3, 1000→50, 1001→50, 50000→50 (올림과 상한 확인).
- page 2개 이상(1, 2, 7, 50)이 요청 URI의 `page`로 전달됨, `size`가 `PAGE_SIZE`(20)로 전달됨.
- page 0, -3 → `page=1`로 보정.
- page 51 → Kakao 호출 없음(server에 expect를 안 등록, `verify()`), 빈 결과 + totalPages 50.

기존 `KakaoSearchServiceFailureTest`: `total_count=41 → totalPages 3` 단정이 새 규칙(`ceil(41/20)=3`)과 같아 **수정하지 않았다**. (task 30에서 "현재 /20 동작을 고정하는 테스트"라 했는데, PAGE_SIZE를 20으로 확정해 결과가 일치.) 같은 이유로 `KakaoSearchServiceTest`, `ExternalApiUrlEncodingTest`(URI 경로/query/fragment만 단정, 파라미터 추가에 영향 없음)도 그대로 통과.

## 4. 테스트 결과 (직접 실행)
- `cd demo && ./gradlew test` → **BUILD SUCCESSFUL** (35s).
- 결과 XML 합계(직접 집계): tests 350, skipped 9, failures 0, errors 0.
- skipped 9개는 모두 `ContentRepositoryPostgresTest`(9/9 skipped). 이 PC에서 Docker가 꺼져 있어(task 29와 같은 상황, 이번에 Docker 상태를 따로 확인하진 않음) Postgres 테스트는 실행되지 않았다. 이번 변경과는 무관하지만 **Postgres 쿼리 검증은 이 실행에서 하지 않은 것**이다.
- 신규 `KakaoSearchServicePagingTest` 16개, `KakaoSearchServiceFailureTest` 12개, `KakaoSearchServiceTest` 9개 모두 통과.
- **실제 Kakao API 호출 확인: 미확인(수동 확인 필요).**

## 5. 수동 확인 절차 (앱에서)
1. 백엔드와 프론트를 띄우고 로그인한 뒤 카테고리 `책(BOOK)`으로 결과가 많은 검색어(예: "해리포터")를 검색한다.
2. 1페이지 결과를 기억한 뒤 "다음"을 눌러 2페이지로 간다 → **결과 목록이 1페이지와 달라야 한다**(수정 전엔 같았음). 페이지당 최대 20건인지도 센다.
3. 하단 "N / M 페이지"의 M이 `ceil(총 개수 / 20)`인지 확인한다(총 개수가 1,000 초과면 M=50).
4. 마지막 페이지(M)로 가서 결과가 비어 있지 않은지, "다음"이 비활성인지 확인한다. (중복 문서 때문에 마지막 페이지가 비어 보일 수 있음: 후속 항목 1)
5. 결과가 적은 검색어(예: 총 21개 이하)는 1/2 페이지로 나오는지, 결과 0건이면 "검색 결과가 없어요"인지 확인한다.
6. (선택) 주소창에서 `page=51`로 직접 이동하면 빈 결과가 나오는지 확인한다.
7. 통합 검색(ALL)에서도 2페이지에 책 결과가 바뀌는지 확인한다(TMDB와 섞여 정렬되므로 눈으로 구분이 어려울 수 있음).

## 6. 후속 항목 (기록만, 수정 안 함)
1. `is_end`/`pageable_count`(중복 제외) 기반 계산 또는 `hasNext` 추가: 실호출로 의미를 확인한 뒤 결정. `total_count`는 중복 포함이라 마지막 페이지가 어긋날 수 있다.
2. Kakao `page > 50` 요청의 실제 응답과 `size=20` 허용 여부 실호출 확인.
3. `total_count`가 1,000 초과일 때 "총 N개" 표시가 접근 가능 범위(1,000건)를 넘음. 표시 정책은 별도 결정.
4. TMDB `total_pages`가 필터링 전 값이라 페이지당 결과 수와 어긋남(task 30의 5절).
5. `searchAll`의 `totalPages`는 max, `totalResults`는 합이라 provider별 크기가 달라도 같은 page로 합침: Kakao와 TMDB가 이제 둘 다 20건이라 어긋남이 줄었지만 구조 한계는 그대로.
6. 통합 검색(ALL)에서 Kakao `page > 50`이면 Kakao 결과만 빠지고 TMDB는 계속 나옴(의도된 부수 효과).
7. `currentPage` 항상 0(기존 기록).

## 7. 정리 (노션용)
1. **문제 상황**: Kakao 호출이 `size`를 안 보내 기본값 10으로 응답하는데 서버는 20으로 나눠 페이지 수를 계산했고, `page`도 안 보내 2페이지 이후에도 1페이지가 반복됐다.
2. **고려한 선택지**: ① page/size를 명시하고 같은 상수로 올림 계산(채택) ② `pageable_count`/`is_end` 기반 계산 ③ 응답에 hasNext 추가.
3. **선택 이유**: ①은 변경이 Kakao 서비스 한 곳이고 DTO·프론트가 그대로다. 트레이드오프: 중복 제외가 반영되지 않아 마지막 페이지가 어긋날 수 있고(②③은 실호출 확인 후), page 50 초과는 호출 없이 빈 결과로 처리한다.
4. **막혔던 부분과 해결**: page 상한 초과를 어떻게 다룰지(줄여 호출 / 그대로 호출 / 미호출)를 비교해, 반복 결과와 실패 정보 손실을 피하려고 미호출을 택했다. 실호출 확인이 불가능해 MockRestServiceServer로 실제 나가는 URI를 검증했다.

## 8. 복습 질문

<details>
<summary>Q1. 페이지 크기를 Kakao 기본값에 맡기고 서버가 따로 20으로 계산하면 왜 위험한가? 이번에는 어떻게 한 곳에서 맞췄나?</summary>

답:

</details>

<details>
<summary>Q2. 올림 계산을 `(totalCount + PAGE_SIZE - 1) / PAGE_SIZE`로 쓴 이유는? `totalCount / PAGE_SIZE`만 쓰면 total_count가 21일 때 어떻게 되나? 0, 20, 21, 40, 41일 때 각각 값은?</summary>

답:

</details>

<details>
<summary>Q3. (기존 코드 연결) Kakao 문서(Document)는 UnifiedSearchResponse로 변환되고 페이지 정보는 SearchResponse에 담긴다. SearchService.searchAll이 Kakao와 TMDB의 totalPages를 어떻게 합치며, 두 provider의 페이지 크기가 다르면 왜 문제가 되나? (이번 수정으로 무엇이 달라졌나)</summary>

답:

</details>
