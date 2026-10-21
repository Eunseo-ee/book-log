# 11. 하이라이트 탭 분리 ("영화/드라마" → "영화" + "드라마")

- 참고: `.claude/tasks/09-highlight-save-ui.md` 발견 사항 1, `.claude/tasks/07-highlight-save-ui-investigation.md` Q3, backlog "하이라이트 '영화/드라마' 탭이 MOVIE만 보내서…"
- 범위: 프론트만. 백엔드 코드·테스트는 수정하지 않는다.

## 요구사항
1. 탭을 "전체 / 책 / 영화 / 드라마 / 애니" 5개로 만든다.
2. 탭이 서버로 보내는 값: 전체=파라미터 없음, 책=`BOOK`, 영화=`MOVIE`, 드라마=`TV`, 애니=`ANIME`(3종 확장은 백엔드).
3. 프론트 `Category` 타입·`CATEGORY_BADGE` 등에 TV가 있는지 백엔드 enum과 대조한다.
4. 탭 상태, 연도 필터, `useHighlights` refetch 동작은 유지한다.

## 사전 확인 (사실, 코드 기준)

### 1) 백엔드 enum vs 프론트 타입: 일치
| | 값 |
|---|---|
| 백엔드 `domain/Category.java` | `BOOK, MOVIE, TV, ANIME, ANIME_MOVIE, ANIME_TVA, ALL` |
| 프론트 `types/index.ts:3` `Category` | `'BOOK' \| 'MOVIE' \| 'TV' \| 'ANIME' \| 'ANIME_MOVIE' \| 'ANIME_TVA' \| 'ALL'` |
- 이름·순서가 같다. 이름이 다르지 않으므로 임의로 정한 값은 없다. 단 TS 타입은 컴파일 시점에만 있어서 서버가 보내는 값을 런타임에 검사해 주지는 않는다. 이 대조는 코드를 눈으로 비교한 것이다.

### 2) TMDB 드라마는 `TV`로 저장된다
- `TmdbSearchService.java:101`: `itemCategory = isMovie ? Category.MOVIE : Category.TV` (애니 장르면 `ANIME_MOVIE`/`ANIME_TVA`, `:99`). 검색 결과의 category가 그대로 저장 요청에 실려 `Content.category`가 되므로 드라마 = `TV`다.
- 하이라이트 조회는 부모 `Content.category`로 거른다(`HighlightService.resolveCategories` → `HighlightSpecifications.categoryIn`).

### 3) 서버의 category 처리
- `HighlightService.resolveCategories`(`:92-100`): null/ALL → 필터 없음, ANIME → ANIME+ANIME_MOVIE+ANIME_TVA, 나머지 → 단일. `TV`는 단일 `[TV]`로 조회된다. 백엔드 수정 필요 없음.

### 4) TV가 빠진 매핑 확인
| 위치 | TV 항목 |
|---|---|
| `CATEGORY_LABEL` (`lib/constants.ts`) | 있음 (`드라마`) |
| `CATEGORY_BADGE` | 있음 (`bg-orange-100 text-orange-800`) |
| `CATEGORY_ACCENT` / `CATEGORY_DOT` | 있음 |
| `CATEGORY_FILTER_TABS` | **없음 ← 이번에 추가** |
- 배지는 `HighlightCard.tsx:32`가 `CATEGORY_BADGE[category] ?? 기본색`으로 그린다. TV는 이미 매핑되어 있어 수정할 곳이 없다.

## 설계 방향
- `CATEGORY_FILTER_TABS`(`lib/constants.ts`)의 `{ label: '영화/드라마', value: 'MOVIE' }` 한 줄을 `{ 영화, MOVIE }`, `{ 드라마, TV }` 두 줄로 나눈다. 타입은 기존 `Category | 'ALL'`을 그대로 쓴다(하드코딩 문자열 아님, 오타는 컴파일 에러).
- `HighlightList`는 수정하지 않는다: 탭은 `CATEGORY_FILTER_TABS.map`으로 그려지고, `key={value}`라 값이 모두 달라 중복 key 문제도 없다. 선택값 `category`는 `'ALL'`이면 `undefined`로 바꿔 `useHighlights(year, category)`에 넘기고(`HighlightList.tsx:19-22`), `getHighlights`가 `ALL`/undefined면 파라미터를 생략한다(`lib/api.ts:108`). → 호출 방식 변화 없음.
- 애니 3종 확장은 프론트에 두지 않는다(규칙 한 곳 = 백엔드).
- `useHighlights`는 `[year, category]`가 바뀔 때 refetch한다. 탭이 늘어도 같은 의존성이라 동작이 같다. 탭 변경 시 호출 방식이 바뀐 곳은 없다.

## 테스트 항목
- [ ] `npx tsc --noEmit`
- [ ] 임시 폴더 복사 빌드 (`.next`를 쓰는 next dev가 3000에 떠 있을 수 있음)
- [ ] `./gradlew test` (백엔드 무수정, 기존과 같아야 함)
- [ ] 조회 GET curl: `category=TV`, `category=MOVIE` (데이터를 바꾸지 않는 요청만)
- [ ] 브라우저 시나리오(아래, 사용자가 직접 실행)

## 범위 밖 (하지 않음)
백엔드 코드·테스트, 저장/수정 후 필터 이탈 토스트, `contents/filter` API, 호출 방식 통일, `HighlightRepositoryTest`/`DemoApplicationTests`.

---

## 구현 결과
- 수정: `frontend/lib/constants.ts` `CATEGORY_FILTER_TABS` 1줄 → 2줄(`영화/MOVIE`, `드라마/TV`). 탭 순서: 전체, 책, 영화, 드라마, 애니.
- `HighlightList.tsx`, `useHighlights.ts`, `lib/api.ts`, `types/index.ts`는 수정하지 않았다(위 설계 방향 참고). 배지/라벨/accent/dot에 TV가 이미 있어 추가 수정 없음.

## 검증 결과
- [x] `npx tsc --noEmit`: 통과(exit 0, 출력 없음).
- [x] 빌드: 통과(exit 0, `/highlights` 6.11 kB). 3000번 포트에 `next dev`(PID 13488)가 떠 있어서 `npm run build`를 프로젝트에서 돌리지 않았다. 소스를 `%TEMP%`의 임시 폴더로 복사하고 `node_modules`는 junction으로 연결해 빌드한 뒤 폴더를 지웠다. 프로젝트의 `.next`는 건드리지 않았다. (첫 시도는 junction이 안 만들어져 `next`를 못 찾아 실패했고, PowerShell `New-Item -ItemType Junction`으로 다시 했다.)
- [ ] `npm run lint`: 스크립트·ESLint 없음(09와 동일). 실행하지 않았다.
- [x] `./gradlew test`: 통과. 처음 실행은 `:test UP-TO-DATE`로 캐시되어 실제로는 안 돌았다. `cleanTest test`로 다시 돌려 **86개 테스트, 실패 0, 에러 0, 스킵 0**(XML 결과 합산). 백엔드는 수정하지 않았다.
  - 주의: `HighlightRepositoryTest`와 `DemoApplicationTests`는 `application.yml`을 읽어 **실제 Supabase에 붙는다**(backlog에 이미 있음). 이번 실행 로그에도 Hikari 풀 2개가 열렸다 닫혔다. 이 테스트들은 수정하지 않았다.
- [x] 조회 GET(8080에 떠 있는 백엔드, 데이터 변경 없음), 현재 DB의 하이라이트는 2건(BOOK 1, ANIME_MOVIE 1):

  | 요청 | 결과 |
  |---|---|
  | `category=ALL` | 2건 (ANIME_MOVIE, BOOK) |
  | `category=BOOK` | 1건 (BOOK) |
  | `category=ANIME` | 1건 (ANIME_MOVIE) ← 백엔드가 ANIME를 3종으로 펼침 |
  | `category=MOVIE` | 0건 |
  | `category=TV` | 0건 |
  | `year=2026&category=TV` | `[]` |
  - 서버가 `category=TV`를 받아 에러 없이 처리하는 것까지는 확인했다. **TV 하이라이트가 실제로 나오는지는 DB에 TV/MOVIE 하이라이트가 없어 확인하지 못했다.** 아래 시나리오에서 직접 만들어 확인해야 한다.
- [ ] 브라우저 확인: 실행하지 않았다(아래 시나리오).

## 브라우저 확인 시나리오 (직접 실행)
준비: 백엔드 8080, `next dev` 3000. DevTools Network 탭을 열고 `/api/highlights` 요청을 본다. 현재 DB엔 영화/드라마 하이라이트가 없으므로 먼저 만든다.
1. **준비 데이터**: 검색에서 TMDB 영화 1개(→ `MOVIE`), 드라마 1개(→ `TV`)를 저장하고, 각각에 하이라이트를 추가한다(모달의 작품 목록에서 선택). 극장판 애니는 이미 1건(ANIME_MOVIE) 있다. TV 애니(ANIME_TVA)도 하나 있으면 7번이 더 정확하다.
2. **탭 구성**: `/highlights`의 탭이 `전체 / 책 / 영화 / 드라마 / 애니` 5개인지, "영화/드라마"가 없는지.
3. **드라마 탭**: 클릭 시 요청 URL이 `/api/highlights?` 뒤에 `category=TV`이고, 드라마 카드(주황 "드라마" 배지)만 나오는지.
4. **영화 탭**: 요청 URL이 `category=MOVIE`이고 드라마 카드가 섞이지 않는지(영화 배지만).
5. **책 탭**: `category=BOOK`, 책 카드만.
6. **전체 탭**: 요청 URL에 `category` 파라미터가 **없고**, 책·영화·드라마·애니가 전부 나오는지.
7. **애니 탭**: 요청 URL이 `category=ANIME` **하나뿐**(ANIME_MOVIE, ANIME_TVA로 따로 요청하지 않음)인데, 극장판과 TV 애니가 함께 나오는지.
8. **연도 필터 조합(AND)**: "드라마" 탭 + 작품 시청일이 있는 연도를 선택하면 URL이 `year=…&category=TV`이고 그 연도의 드라마만 나온다. 시청일이 없는 연도(예: 2023)면 빈 목록 문구가 나온다. 연도를 "전체 연도"로 되돌리면 드라마가 다시 모두 나온다.
9. **탭 전환 중 연도 유지**: 연도를 고른 채 탭을 바꿔도 연도 값이 유지되고 URL에 `year`가 계속 붙는지.
10. **수정/삭제 후 탭 유지**: 드라마 탭에서 카드의 문장을 수정하면 저장 후에도 같은 탭에 남는지(10번 시나리오 6과 같은 동작).
11. **배지**: 드라마 카드의 배지가 "드라마"(주황)이고 왼쪽 accent도 주황인지, 모달에서 드라마를 고를 때 배지가 같은지.

## 변경 파일 (커밋하지 않음)
- 수정: `frontend/lib/constants.ts`
- 신규: `.claude/tasks/11-split-movie-tv-tab.md` (이 파일)
- 수정하지 않음: 백엔드 전부, `HighlightList.tsx`, `useHighlights.ts`, `lib/api.ts`, `types/index.ts`, `package.json`, `.claude/backlog.md`, `decisions.md`, `CLAUDE.md`

## 발견 사항 (범위 밖, 기록만)
1. backlog의 "하이라이트 '영화/드라마' 탭이 MOVIE만 보내서 TV 하이라이트가 빠짐" 항목은 이 작업으로 해소된다(브라우저 확인 후 사용자가 체크). 기존 줄은 덮어쓰지 않았다.
2. 영화·드라마 하이라이트가 DB에 없어서, 서버 쪽 TV 필터의 실제 결과 확인은 사용자 시나리오로 넘겼다.

---

## 정리 (트러블슈팅용)

### 1. 문제 상황
하이라이트 목록의 탭이 "전체 / 책 / 영화/드라마 / 애니"였고 "영화/드라마" 탭은 `category=MOVIE`만 서버로 보냈다. TMDB 드라마는 `TV`로 저장되므로(`TmdbSearchService`) 드라마 하이라이트는 "전체"를 제외한 어느 탭에서도 보이지 않았다. 백엔드는 이미 단일 카테고리 조회를 지원했다.

### 2. 고려한 선택지
- A. 프론트에서 탭을 "영화"(MOVIE)와 "드라마"(TV)로 나눈다.
- B. 백엔드 `resolveCategories`에서 MOVIE를 MOVIE+TV로 묶고 탭은 그대로 둔다(애니와 같은 방식).
- C. 프론트가 "영화/드라마" 탭에서 MOVIE, TV 두 번 요청해 합친다.

### 3. 선택 이유 (트레이드오프)
- **A**를 골랐다. API가 단일 category를 이미 지원하므로 상수 배열 한 줄만 바꾸면 되고, 서버 규칙도 필터 의미도 그대로다. 비용은 탭이 하나 늘어 폭이 넓어진다는 점, 그리고 "영화와 드라마를 한 번에 보기"는 "전체" 탭 말고는 없어진다는 점이다.
- B는 사용자에게는 탭이 그대로지만 백엔드 규칙·테스트를 바꿔야 하고, `MOVIE`가 의미상 영화만이 아니게 되어 검색(`TmdbSearchService`는 MOVIE와 TV를 구분)과 의미가 어긋난다.
- C는 두 요청을 합치고 정렬(`createdAt desc, id desc`)을 프론트가 다시 맞춰야 해서, 규칙이 두 곳에 생긴다(애니 확장을 프론트로 가져오지 않기로 한 이유와 같다).

### 4. 막혔던 부분과 해결
- **`./gradlew test`가 실제로 안 돌았다.** 첫 실행이 `:test UP-TO-DATE`였다. 입력(소스)이 안 바뀌면 Gradle이 이전 결과를 재사용해서다. `cleanTest test`로 강제해 86개 통과를 확인했다. "통과"라고 쓰려면 실제 실행인지 먼저 봐야 한다.
- **임시 폴더 빌드의 junction이 안 만들어져** `next`를 찾지 못했다. Git Bash의 `/tmp` 경로가 Windows 경로와 달라서였고, PowerShell `%TEMP%`로 다시 했다.
- **DB에 영화/드라마 하이라이트가 없어** 새 탭의 "실제로 나오는지"는 검증하지 못했다. 서버가 `category=TV`를 에러 없이 처리하는 것까지만 GET으로 확인하고, 나머지는 시나리오 1에서 데이터를 만들어 확인하도록 남겼다.

### 개념 메모
- **TS 유니온 타입은 컴파일 시점에만 있다**: `Category | 'ALL'`로 탭 값의 오타(예: `'Tv'`)는 컴파일 에러가 되지만, 서버가 `'TV'`라는 문자열을 실제로 받아 주는지는 타입이 보장하지 않는다. 그래서 백엔드 enum을 코드로 대조했다.
- **Gradle UP-TO-DATE**: 입력과 출력이 이전과 같으면 태스크를 건너뛴다. 테스트를 다시 돌리려면 `cleanTest`가 필요하다.

---

## 복습 질문 (프론트엔드 → 답 포함)
<details>
<summary>1. "드라마" 탭을 눌렀을 때 <code>HighlightList</code>의 어느 상태가 어떻게 바뀌고, 그것이 어떤 코드를 거쳐 <code>?category=TV</code> 요청이 되는가? "전체" 탭이면 URL은 어떻게 다른가?</summary>

답: `onClick`이 `setCategory('TV')`를 호출한다. `HighlightList`가 `category === 'ALL' ? undefined : category`로 `useHighlights(year, 'TV')`를 부르고, `useHighlights`의 `useEffect` 의존성 `[year, category]`가 바뀌어 `refetch()`가 돈다. `getHighlights`가 `params.set('category', 'TV')`를 해서 `/api/highlights?category=TV`가 된다. "전체"는 `'ALL'` → `undefined`라서 `category` 키 자체가 URL에 없다(`year`만 있거나 쿼리가 비어 있음).

</details>

<details>
<summary>2. 탭 배열을 <code>{ label: '드라마', value: 'tv' }</code>처럼 소문자로 썼다면 <code>tsc</code>는 잡아 주는가? 서버는 어떻게 반응하겠는가?</summary>

답: 잡아 준다. 배열 타입이 `{ value: Category | 'ALL' }[]`라 `'tv'`는 유니온에 없어 컴파일 에러다. 만약 타입을 `string`으로 느슨하게 쓰면 통과하고, 서버는 쿼리 파라미터를 enum으로 변환하다 실패해 400을 돌려줄 것이다(Spring이 `Category`로 바인딩 못 함). 그래서 하드코딩 문자열 대신 `Category` 타입을 쓰는 것이 의미 있다. 다만 타입은 서버 enum과 같은지까지 보장하지는 않아서 이번처럼 코드로 한 번 대조해야 한다.

</details>

<details>
<summary>3. (기존 코드 연결) "애니" 탭은 <code>ANIME</code> 하나만 보내는데 극장판(<code>ANIME_MOVIE</code>)도 나온다. 어디서 펼쳐지고, 왜 프론트가 아니라 거기에 두었나? 같은 방식으로 "영화" 탭에 드라마를 섞으려면 어디를 고쳐야 하나?</summary>

답: `HighlightService.resolveCategories`가 `ANIME`를 `ANIME + ANIME_MOVIE + ANIME_TVA`로 펼치고, 그 목록으로 `HighlightSpecifications.categoryIn`이 부모 `Content.category`를 `IN`으로 거른다. 규칙이 한 곳에 있어야 프론트와 백엔드가 어긋나지 않아서다. 영화에 드라마를 섞으려면 `resolveCategories`에 `MOVIE → [MOVIE, TV]` 분기를 추가하고 서비스 테스트도 고쳐야 한다. 이번에는 탭 분리(선택지 A)를 골라서 하지 않았다.

</details>
