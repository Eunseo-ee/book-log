# 09. 하이라이트 저장 UI (모음집 "하이라이트 추가" 모달)

- 브랜치: `main` (작업 시작 시점 브랜치. 06·08 작업이 커밋되지 않은 상태로 워킹 트리에 있음. 커밋하지 않음)
- 참고: `.claude/tasks/07-highlight-save-ui-investigation.md` Q3 선택지 A, Q4-②, `.claude/tasks/08-cors-global.md`, backlog "HighlightWithContent 형태 불일치" 항목

## 배경
`/highlights` 페이지는 목록(조회·삭제)만 있고 저장 UI가 없다. `lib/api.ts`의 `saveHighlight`는 정의만 있고 호출하는 컴포넌트가 없다(07 Q1).
백엔드 API는 바꾸지 않는다. 필요한 것은 프론트에서만 해결한다.

## 사전 확인 (사실, 코드 기준)

### 1) `saveHighlight` 시그니처 vs 백엔드 `HighlightRequestDto`: 일치
| 필드 | 백엔드 (`HighlightRequestDto.java:15-28`) | 프론트 (`types/index.ts:94-101`) |
|---|---|---|
| `text` | `@NotBlank`, `@Size(max = 1000)` | `text: string` |
| `contentId` | `@NotNull Long` | `contentId: number` |
| `page` | `@Min(1)`, null 허용 | `page?: number` |
| `season` / `episode` | 검증 없음, null 허용 | `season?` / `episode?: number` |
| `timestamp` | 검증 없음(형식 강제 X), null 허용 | `timestamp?: string` |
- `saveHighlight(dto)`는 `POST /api/highlights`, 응답은 새 id(`request<number>`, `lib/api.ts:113-114`). 컨트롤러도 `ResponseEntity<Long>`(`HighlightController.java:42-44`).
- 호출 주소는 `BASE_URL`(8080 직접)이다. 08의 전역 CORS(워킹 트리, 미커밋)로 허용 origin `http://localhost:3000`이면 통과한다. 이번에 호출 방식은 바꾸지 않는다.
- 서비스는 `contentId`가 없는 id면 `IllegalArgumentException("존재하지 않는 콘텐츠입니다. id=...")`를 던진다(`HighlightService.java:36`) → `{ status, message }` 400.

### 2) 저장된 콘텐츠 목록 함수: `getFilteredContents` 있음, 호출처 없음
- `lib/api.ts:92-97` `getFilteredContents(year?, category?)` → `GET /api/contents/filter`, 반환 `ContentResponseDto[]`(`id, title, author, category, viewDate, rating, imgUrl`). 백엔드 DTO(`ContentResponseDto.java`)와 필드 이름이 같다.
- 인자 없이 부르면 백엔드가 `findAll()`(`ContentService.java:84-87`) → 전체 콘텐츠. 작품 선택 목록에 이걸 그대로 쓴다.

### 3) 기존 모달/폼/토스트 패턴
- 모달: `SaveContentForm.tsx` 하나. `fixed inset-0 bg-black/40` 오버레이, `role="dialog"`, ESC/바깥 클릭으로 닫기(저장 중에는 막음), `inputClass`/`labelClass` 상수, 글자 수 카운터, `role="alert"` 에러 목록, 취소/저장 버튼. → **같은 구조와 클래스로 만든다.**
- 에러 변환: `toErrorMessages(err)`(`lib/api.ts:53-66`)가 이미 `{ status, message }`와 `{ 필드명: 메시지 }` 두 형식을 모두 처리한다. → 재사용.
- 토스트: **없다**(프론트 전체 grep `toast` 0건). 새로 만들거나 라이브러리를 추가하지 않는다. 저장 성공 피드백은 "모달 닫힘 + 목록 갱신"으로 한다.
- 아이콘은 이미 의존성에 있는 `lucide-react`만 쓴다.

### 4) 탭 필터 → API `category` 매핑
- 탭(`lib/constants.ts` `CATEGORY_FILTER_TABS`): 전체=`ALL`, 책=`BOOK`, **영화/드라마=`MOVIE`**, 애니=`ANIME`
- `HighlightList`가 `ALL`이면 `undefined`로 바꾸고, `getHighlights`는 `ALL`/없음이면 파라미터를 빼고 보낸다.
- 백엔드 `resolveCategories`(`HighlightService.java:92-100`): null/ALL → 필터 없음, ANIME → ANIME+ANIME_MOVIE+ANIME_TVA, **나머지는 단일 값**.
- **발견: "영화/드라마" 탭은 `MOVIE`만 조회해서 `TV`(드라마) 하이라이트가 빠진다.** 07 발견 사항 2번과 같은 내용. 고치지 않고 기록만 한다(아래 발견 사항).

### 5) 검증 도구 현황
- `package.json` scripts는 `dev`/`build`/`start`뿐이다. **`lint` 스크립트도, ESLint 설치·설정도 없다.** 새 라이브러리 추가 금지라 도입하지 않는다. 대신 `tsc --noEmit`과 `next build`(타입 체크 포함)로 검증한다.
- 프론트 테스트 환경(Jest/Vitest/Testing Library)이 **없다.** 도입하지 않고 "은서 확인 시나리오"로 대신한다.
- 작업 전 기준: `npx tsc --noEmit` 통과(exit 0).

## 목표
`/highlights`에서 저장된 작품을 골라 하이라이트를 저장하고, 저장한 하이라이트가 목록에 제대로 표시된다.

## 설계 방향
- 진입점: `HighlightList` 헤더에 "하이라이트 추가" 버튼 → `AddHighlightModal`(신규, `components/highlights/`).
- 작품 선택: 모달이 열릴 때 `getFilteredContents()`로 전체 콘텐츠를 불러와 `<select>`로 고른다. 옵션은 `[카테고리] 제목`, 선택 후 배지+제목을 보여 준다. 목록이 비면 "먼저 기록을 저장하세요" + `/search` 링크. 불러오기 실패는 빈 목록과 구분해서 에러로 보여 준다.
- 위치 필드는 선택한 작품의 카테고리로 정한다.
  - `BOOK` → `page`
  - `MOVIE`, `ANIME_MOVIE` → `timestamp` (예: `01:23:45`)
  - `TV`, `ANIME_TVA`, `ANIME` → `season`, `episode`
  - 작품을 바꾸면 안 보이는 필드는 전송하지 않는다(입력값이 남아 있어도 보내지 않음).
- 전송 규칙: 비어 있는 선택 필드는 키를 빼고 보낸다(`SaveContentForm`과 같은 관례).
- 클라이언트 검증은 서버 규칙과 같게: text 공백 불가·1000자 이내, 작품 필수, page 1 이상 정수. season/episode는 1 이상 정수, timestamp는 `HH:MM:SS` 형식(서버는 검증하지 않지만 목록 표시 일관성을 위해 프론트에서만 확인).
- 저장 성공 → 모달 닫기 + `refetch()`. 실패 → `toErrorMessages`로 모달 안에 표시(두 에러 형식 모두).
- 목록 타입: `HighlightWithContent`(중첩 `content`)를 백엔드와 같은 이름·형태의 `HighlightResponseDto`(평평한 `contentId/contentTitle/contentCategory`)로 교체하고 `HighlightCard`/`HighlightList` 렌더링·검색을 맞춘다.

## DoD
- [ ] `/highlights`에 "하이라이트 추가" 버튼 → 모달
- [ ] 작품 선택(필수, 제목/카테고리 표시), 빈 목록 안내
- [ ] text 필수 + 1000자 카운터
- [ ] 카테고리별 선택 필드(page / timestamp / season·episode)
- [ ] 성공 시 닫기 + 목록 재조회, 실패 시 서버 메시지(두 형식) 모달 안 표시
- [ ] 목록 타입을 평평한 DTO로 맞추고 카드·검색 렌더링 수정
- [ ] `tsc --noEmit`, `npm run build` 통과 (`npm run lint`는 스크립트 없음 → 기록)
- [ ] 은서 확인 시나리오 작성

## 수정 허용 범위
- `frontend/types/index.ts` (하이라이트 응답 타입)
- `frontend/lib/api.ts` (`getHighlights` 반환 타입만)
- `frontend/hooks/useHighlights.ts` (타입)
- `frontend/components/highlights/HighlightList.tsx`, `HighlightCard.tsx`
- `frontend/components/highlights/AddHighlightModal.tsx` (신규)
- 문서: 이 파일, `.claude/backlog.md`(추가만)

## 범위 밖
백엔드 전부, 탭 "영화/드라마" 매핑 수정, API 호출 방식(프록시/직접) 통일, 하이라이트 수정 기능(`onEdit`), `useHighlights` 실패/빈 목록 구분, ESLint·테스트 도구 도입, 토스트 컴포넌트

---

## 구현 내용
- `frontend/types/index.ts`
  - `HighlightWithContent`(중첩 `content` + `thumbnailUrl`)를 지우고, 백엔드와 같은 이름의 `HighlightResponseDto`를 Response DTOs 구역에 추가했다. 선택 필드(`page/season/episode/timestamp`)는 `number | null`. 백엔드가 값이 없으면 키를 빼지 않고 `null`로 내려주기 때문이다. `content_id`에 NOT NULL 제약이 없어서(`Highlight.java` `@JoinColumn(name = "content_id")`) `contentId/contentTitle/contentCategory`도 `| null`로 뒀다.
- `frontend/lib/api.ts`: `getHighlights` 반환 타입만 `HighlightResponseDto[]`로 바꾸고, "(백엔드 추가 예정 엔드포인트)" 주석을 지웠다. 호출 주소(`BASE_URL` 직접)는 그대로 뒀다.
- `frontend/hooks/useHighlights.ts`: 타입 이름만 바꿨다.
- `frontend/components/highlights/HighlightCard.tsx`: `content.category`/`content.title` 대신 `contentCategory`/`contentTitle`을 쓴다. 카테고리가 null이면 배지를 숨기고, 제목이 null이면 `-`로 표시한다.
- `frontend/components/highlights/HighlightList.tsx`
  - 헤더 검색창 옆에 "하이라이트 추가" 버튼(`:53-60`)을 넣었다. `adding` 상태로 모달을 연다.
  - 클라이언트 검색이 `h.content.title` 대신 `h.contentTitle ?? ''`을 본다(`:35`).
  - 모달의 `onSaved`에서 닫기 + `refetch()`(`:124-130`).
- `frontend/components/highlights/AddHighlightModal.tsx` (신규)
  - 열릴 때 `getFilteredContents()`(`:63`)로 저장된 콘텐츠 전체를 불러와 `viewDate` 내림차순으로 정렬한다. 불러오는 중 / 실패(에러 문구) / 0건("먼저 기록을 저장하세요" + `/search` 링크)을 따로 보여 준다.
  - `<select>` 옵션은 `[카테고리] 제목`이고, 고른 뒤에는 배지와 제목을 한 번 더 보여 준다.
  - `locationKindOf`(`:21`): BOOK → page / MOVIE·ANIME_MOVIE → timestamp / TV·ANIME_TVA·ANIME → season·episode.
  - `buildDto`(`:95`): 검증과 DTO 생성을 한 함수에서 한다. **지금 보이는 위치 필드만** DTO에 담는다. 그래서 책을 골라 page를 입력한 뒤 영화로 바꾸면 page는 보내지 않는다. 빈 선택 필드는 키를 빼서 보낸다.
  - 검증: 작품 필수, 문장 공백 불가·1000자 이내, page/season/episode는 1 이상 정수(`parsePositiveInt`, `:38`), timestamp는 `HH:MM:SS`.
  - 실패 시 `toErrorMessages(err)`로 서버 메시지를 모달 안 `role="alert"` 목록에 띄운다. `{ status, message }`와 `{ 필드명: 메시지 }`를 둘 다 처리하는 기존 함수라 새로 만들지 않았다.
  - 구조와 클래스(오버레이, ESC/바깥 클릭 닫기, 저장 중 닫기 막기, `inputClass`, 카운터, 버튼)는 `SaveContentForm.tsx`와 같다. 다크 테마 클래스도 그대로다.

## 검증 결과
### 실행한 것
- [x] `npx tsc --noEmit`: 통과(exit 0). 작업 전에도 통과했다.
- [x] `npm run build`: 통과(exit 0, `/highlights` 5.66 kB). 3000번 포트에 `next dev`가 떠 있어서, 같은 `.next` 폴더를 덮어쓰지 않도록 소스를 임시 폴더에 복사(node_modules는 junction)해서 빌드했다. 프로젝트의 `.next`는 건드리지 않았다.
- [ ] `npm run lint`: **실행 불가.** `Missing script: "lint"`. ESLint도 설치·설정되어 있지 않다. 새 의존성 추가 금지라 도입하지 않았다. `next build`의 "Linting and checking validity of types" 단계도 ESLint가 없어 타입 체크만 한 셈이다.
- [x] 실행 중인 백엔드(8080)에 **데이터를 쓰지 않는 요청만** 보내 응답 형태를 확인했다(curl).
  - `GET /api/contents/filter` → `id, title, author, category, viewDate, rating, imgUrl`. 프론트 `ContentResponseDto`와 키가 같다. 단 TMDB 작품은 `author: null`(아래 발견 사항 3).
  - `GET /api/highlights` → 200, `[]`. 실행 중인 서버에 06의 GET이 반영되어 있다. 지금 저장된 하이라이트는 0개다.
  - `POST` 공백 text + page 0 + contentId 없음 → 400 `{"contentId":"...","page":"페이지는 1 이상이어야 합니다.","text":"..."}` (@Valid 맵 형식)
  - `POST` 없는 contentId → 400 `{"status":400,"message":"존재하지 않는 콘텐츠입니다. id=999999999"}` (ErrorResponse 형식)
  - `OPTIONS /api/highlights` (Origin `http://localhost:3000`, POST, content-type) → 200. 08 전역 CORS가 실행 중인 서버에 반영되어 있다.
- 프론트 테스트 환경이 없어서 컴포넌트 테스트는 추가하지 않았다.
- **정상 저장(POST 성공)은 실행하지 않았다.** 실행 중인 서버가 실제 DB(Supabase)에 붙어 있어서 테스트 데이터가 남기 때문이다. 아래 시나리오로 은서가 확인한다.

### 은서 확인 시나리오 (백엔드 8080 + `npm run dev` 3000)
1. **진입**: `/highlights` 헤더 오른쪽에 보라색 "하이라이트 추가" 버튼이 보인다. 누르면 모달이 뜨고, 작품 select가 "불러오는 중…" → "작품을 선택하세요"로 바뀐다.
2. **작품 선택 + 필드 전환**
   - 책(예: `[책] 페스트`)을 고르면 "페이지 (선택)"만 보인다.
   - 영화/애니 극장판(예: `[애니 극장판] 귀멸의 칼날: 환락의 거리 결전편`)을 고르면 "시간 (선택)"만 보인다.
   - 드라마/애니 TV를 고르면 "시즌 / 화"가 보인다.
3. **클라이언트 검증**: 작품 없이 저장 → "작품을 선택해주세요." / 문장 비우고 저장 → "문장을 입력해주세요." / 시간에 `1:2` → 형식 안내 / 페이지에 `0` → 1 이상 안내. 문장 칸에 입력하면 `n / 1000` 카운터가 올라가고 1000자에서 더 입력되지 않는다.
4. **저장**: 책을 고르고 문장 + 페이지 `123` 입력 → 저장. 모달이 닫히고 목록에 카드가 생긴다. 카드에 `책` 배지, 작품 제목, `p.123`, 오늘 날짜, 문장이 보인다. (07 Q4-② TypeError 오버레이가 뜨지 않아야 한다)
   - DevTools Network에서 POST 본문에 **보이는 필드만** 있는지 본다. 예: 책에서 page를 비웠다면 `{"text":"...","contentId":78}`만 간다.
5. **서버 에러 표시**(선택): 모달을 연 상태에서 백엔드를 끄고 저장 → "서버에 연결할 수 없습니다..."가 모달 안에 뜨고 모달은 닫히지 않는다.
6. **탭/연도 필터**
   - "책" 탭: 방금 저장한 책 하이라이트만 보인다. "애니" 탭: 애니 극장판 하이라이트가 보인다(백엔드가 ANIME_MOVIE/ANIME_TVA까지 묶음).
   - "영화/드라마" 탭: MOVIE 하이라이트는 보이지만 **TV(드라마) 하이라이트는 안 보인다**(아래 발견 사항 1. 버그지만 이번 범위 밖).
   - 연도: 작품의 `viewDate` 연도(예: 2026)를 고르면 보이고, 2025를 고르면 사라진다. 기준은 하이라이트 작성일이 아니라 작품 관람일이다(decisions.md).
   - 검색창에 작품 제목 일부를 치면 그 작품의 하이라이트만 남는다.
7. **빈 목록 안내**(선택): 콘텐츠가 하나도 없는 DB라면 모달에 "먼저 기록을 저장하세요."와 "작품 검색하러 가기" 링크가 보인다. 지금 DB에는 콘텐츠가 있어서 실제로는 보기 어렵다.

---

## 변경 파일 (커밋하지 않음)
- 신규: `frontend/components/highlights/AddHighlightModal.tsx`
- 수정: `frontend/components/highlights/HighlightList.tsx`, `HighlightCard.tsx`, `frontend/hooks/useHighlights.ts`, `frontend/lib/api.ts`(타입·주석만), `frontend/types/index.ts`
- 신규: `.claude/tasks/09-highlight-save-ui.md` (이 파일)
- 수정: `.claude/backlog.md` (발견 사항 추가만)
- 수정하지 않음: 백엔드 전부, `next.config.js`, `package.json`, `lib/constants.ts`. 06·08 작업 파일은 작업 전부터 워킹 트리에 있었고 건드리지 않았다.

## 발견 사항 (범위 밖, 기록만)
1. **"영화/드라마" 탭은 `MOVIE`만 조회한다.** 탭 값이 `MOVIE` 하나(`lib/constants.ts` `CATEGORY_FILTER_TABS`)이고, 백엔드 `resolveCategories`는 ANIME만 묶고 나머지는 단일 값으로 처리한다(`HighlightService.java:92-100`). 그래서 TV로 저장한 드라마 하이라이트는 "전체"에서만 보인다. 고치려면 (a) 백엔드에서 MOVIE를 MOVIE+TV로 묶거나(애니와 같은 방식, decisions 추가 필요) (b) 탭을 영화/드라마로 나누는 방법이 있다. API는 단일 category라 프론트만으로 두 값을 한 번에 보낼 수 없다. 07 발견 사항 2번과 같은 내용. → backlog
2. **저장한 하이라이트가 현재 필터에 안 걸리면 저장되었는데도 목록에 안 나온다.** 예: "책" 탭을 보면서 영화 하이라이트를 저장하면, 모달은 닫히지만 목록에는 변화가 없다. 토스트가 없어서 성공 여부를 알 수 없다. 인박스 "저장 성공 후 어디서 볼 수 있는지 안내 없음"과 같은 종류. → backlog
3. **`ContentResponseDto.author`가 프론트에서 `string`인데 TMDB 작품은 `null`로 온다**(curl 확인, id 73). 이번 모달은 author를 쓰지 않아서 영향이 없다. 검색 타입의 `authorOrDirector` 항목(backlog)과 같은 문제다. → backlog
4. **프론트에 `lint` 스크립트·ESLint·테스트 도구가 없다.** 요청한 `npm run lint` 검증을 할 수 없었다. 도입 여부는 따로 결정이 필요하다. → backlog
5. `getFilteredContents()`를 조건 없이 부르면 백엔드가 `findAll()`로 **모든 사용자의** 콘텐츠를 준다(`ContentService.java:84-87`, userId 조건 없음). 지금은 사용자 1명(userId=1)이라 문제없지만 JWT 병합 시 작품 선택 목록에 남의 작품이 섞인다. 백엔드 수정이 필요해 손대지 않았다. → backlog
6. (기존 backlog) "프론트 HighlightWithContent와 HighlightResponseDto 형태 불일치"는 이번에 프론트 쪽으로 맞췄다. 다만 커밋 전이고 브라우저 확인 전이라 08과 같은 관례로 backlog 체크는 하지 않았다.

---

## 정리 (트러블슈팅용)

### 1. 문제 상황
하이라이트 저장 API(`POST /api/highlights`)와 프론트 함수 `saveHighlight`는 있었지만, 이를 부르는 화면이 없었다. 저장에 필요한 `contentId`를 화면에서 얻을 경로도 없었다. 게다가 목록 타입이 백엔드 응답과 달랐다. 프론트는 중첩 `content.title`을 기대했고, 백엔드는 평평한 `contentTitle`을 보낸다. 그래서 하이라이트가 한 개라도 생기면 카드 렌더링에서 TypeError가 날 상태였다.

### 2. 고려한 선택지
- 작품 선택 방식
  - A. 모달이 열릴 때 저장된 콘텐츠 전체를 `GET /api/contents/filter`로 받아 `<select>`로 고르기
  - B. 모달 안에 작품 검색창을 두고, 입력할 때마다 서버에서 찾기
  - C. 콘텐츠 저장 폼(`SaveContentForm`) 직후 이어서 입력하기 (`saveContent`가 돌려주는 id 사용)
- 위치 필드 처리
  - A. 작품 카테고리에 맞는 필드만 보여 주고, 보이는 필드만 전송
  - B. page/season/episode/timestamp를 항상 다 보여 주기

### 3. 선택 이유 (트레이드오프)
- 작품 선택은 **A**. 기존 API를 그대로 쓰고 백엔드를 바꾸지 않는다. 개인 기록이라 작품 수가 수십 개 수준이라 한 번에 받아도 부담이 적다. 비용은 작품이 수백 개가 되면 select가 길어진다는 점이다. 그때 B(검색)로 바꾸면 되는데, B는 백엔드에 제목 검색 API가 없어서 이번 범위("백엔드 변경 없음")를 넘는다. C는 이미 저장한 작품에는 문장을 추가할 수 없다(07 Q3).
- 위치 필드는 **A**. 책에 "시즌"을 입력하는 식의 의미 없는 데이터가 생기지 않는다. 작품을 바꿨을 때 숨겨진 필드의 값이 같이 전송되는 실수도 막았다(`buildDto`가 보이는 필드만 담음). 비용은 카테고리→필드 매핑을 프론트가 알고 있어야 한다는 점이다. 백엔드는 카테고리와 필드 조합을 검증하지 않는다.
- 목록 타입은 백엔드 DTO 이름(`HighlightResponseDto`)과 형태를 그대로 따랐다. 다른 타입(`ContentResponseDto`, `CalendarResponse`)도 자바 클래스 이름을 쓰는 관례라 맞췄다. `null`로 오는 값은 `| null`로 적었다. `?:`(키 없음)로 적으면 `null`을 다루는 코드를 타입이 강제하지 않는다.

### 4. 막혔던 부분과 해결
- **`tsc`가 원래부터 통과하고 있었다.** 타입이 틀렸는데도 에러가 없었던 이유: 프론트 타입은 "백엔드가 이렇게 줄 것"이라는 약속일 뿐이고, `res.json()`의 결과는 검사 없이 그 타입으로 믿는다. 타입을 백엔드 DTO에 맞춰 고치자 `h.content.title` 같은 잘못된 접근이 그제야 컴파일 에러 대상이 됐다. 실제 응답 형태는 curl로 확인했다.
- **`npm run build`를 그대로 돌릴 수 없었다.** `next dev`가 3000에서 같은 `.next` 폴더를 쓰고 있어서, 빌드가 덮어쓰면 은서의 dev 서버가 깨질 수 있다. 소스를 임시 폴더에 복사하고 node_modules는 junction으로 연결해 빌드했다.
- **`npm run lint`는 스크립트가 없었다.** ESLint를 추가하면 새 의존성이 되므로 하지 않고, `tsc`와 `next build`로 대신하고 기록했다.
- **저장 성공 경로는 실제 DB라 실행하지 않았다.** 대신 실패 경로 두 개(@Valid 맵, ErrorResponse)를 데이터를 쓰지 않는 요청으로 확인했고, 성공 경로는 확인 시나리오로 넘겼다.

### 개념 메모
- **타입은 런타임 검사가 아니다**: TypeScript 타입은 컴파일할 때만 존재한다. `fetch` 응답을 `HighlightWithContent[]`라고 선언해도 실제 JSON 모양은 검사하지 않는다. 그래서 백엔드와 다른 타입은 컴파일을 통과하고 런타임에 `undefined.category`로 터진다. 타입을 서버 DTO와 맞추면 이런 버그를 컴파일 단계에서 잡을 수 있다.

---

## 복습 질문 (프론트엔드 → 답 포함)
<details>
<summary>1. 책을 골라 페이지에 <code>50</code>을 입력한 뒤 작품을 영화로 바꾸고 시간 <code>01:00:00</code>을 넣어 저장하면, POST 본문에 <code>page</code>가 들어가는가? 어느 코드가 그걸 결정하는가?</summary>

답: 들어가지 않는다. `page` state에는 `'50'`이 남아 있지만, `buildDto`(`AddHighlightModal.tsx:95`)는 현재 작품의 `kind`(`locationKindOf(selected.category)`)가 `'timestamp'`일 때 `timestamp`만 `location`에 담는다. 그래서 본문은 `{ text, contentId, timestamp: "01:00:00" }`이다. 입력값(state)과 전송값(DTO)을 분리했기 때문에, 작품을 다시 책으로 바꾸면 50이 그대로 남아 있다.

</details>

<details>
<summary>2. 고치기 전 <code>HighlightWithContent</code> 타입으로도 <code>tsc</code>가 통과했다. 그런데 하이라이트가 한 개 생기면 화면이 깨졌을 것이다. 어느 줄에서 어떤 에러가 났을까? 왜 컴파일러가 못 잡았을까?</summary>

답: `HighlightCard`의 `const category = content.category`에서 `TypeError: Cannot read properties of undefined (reading 'category')`가 난다. 백엔드는 `content` 키 없이 `contentTitle/contentCategory`를 평평하게 보내서 `content`가 `undefined`이기 때문이다. 컴파일러는 `request<T>`가 `res.json()`을 `T`라고 믿고 넘기는 것만 알고, 실제 JSON은 보지 않는다. 타입은 약속이지 검증이 아니다.

</details>

<details>
<summary>3. (기존 코드 연결) 문장을 비우고 작품도 고르지 않은 채 클라이언트 검증을 건너뛰어 서버로 보냈다고 하자. 서버 응답 본문은 어떤 모양이고, <code>toErrorMessages</code>는 그걸 어떻게 화면 문구로 바꾸는가? 반대로 없는 contentId를 보내면?</summary>

답: `@Valid` 실패라 `GlobalExceptionHandler.handleValidationExceptions`가 `{ "text": "하이라이트 내용은 필수입니다.", "contentId": "연관된 콘텐츠 ID는 필수입니다." }` 같은 필드 맵을 400으로 준다. `toErrorMessages`(`lib/api.ts:53-66`)는 먼저 `body.message`가 문자열인지 보고, 없으면 `Object.values(body)` 중 문자열만 모아 목록으로 돌려준다. 그래서 두 줄이 뜬다. 없는 contentId는 검증은 통과하고, 서비스의 `findById(...).orElseThrow(IllegalArgumentException)`(`HighlightService.java:36`)에서 걸린다. 응답은 `{ status: 400, message: "존재하지 않는 콘텐츠입니다. id=..." }`라서 `message` 한 줄이 뜬다. 같은 400이라도 어디서 막혔는지(컨트롤러 @Valid vs 서비스)에 따라 형식이 다르다. backlog "평점 범위 오류 응답 형식이 컨트롤러(@Valid)와 서비스에서 다름"과 같은 구조다.

</details>
