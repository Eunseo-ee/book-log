# 07. 하이라이트 저장 버튼이 안 보이는 원인 조사

- 브랜치: `main` (조사 시점 브랜치. 06 작업이 커밋되지 않은 상태로 워킹 트리에 있음)
- 참고: `.claude/tasks/06-highlight-query.md` "발견 사항" 3번, `.claude/backlog.md` 하이라이트 프록시/형태 불일치 항목

## 목표
`npm run dev`로 띄운 프론트에서 하이라이트를 저장하는 버튼이 보이지 않는 원인을 코드로 조사한다.
항목마다 판단을 "확인(코드)", "추측", "미확인"으로 나누고, 근거를 파일:줄로 남긴다. 코드는 수정하지 않는다.

## 증상 (은서 확인)
1. 프론트(Next.js, `frontend/`)를 `npm run dev`로 실행했는데 하이라이트 저장 버튼이 안 보인다.
2. 보고 있던 화면: **미기재** (요청서의 `[예: 콘텐츠 상세 페이지 / 기록 목록 / 검색 결과 / 잘 모르겠음]`이 템플릿 그대로 남아 있음)
3. 백엔드 `POST /api/highlights`는 이미 있다 (필수: `text`, `contentId`).

## 조사할 것
- Q1. `frontend/`에서 `/api/highlights`를 호출하는 코드 (POST/GET, 파일·컴포넌트)
- Q2. 하이라이트 저장 UI(버튼/폼/모달) 구현 여부와 렌더링 조건
- Q3. 없다면 어느 화면에 어떤 형태로 넣을지 선택지 2~3개와 트레이드오프
- Q4. 브라우저 콘솔/렌더링 에러 가능성

## 범위
- 수정 허용: 이 파일
- 범위 밖: 모든 코드/설정 파일 수정, `inbox.md`·`backlog.md` 수정, 커밋, push

---

## 조사 결과 요약
| 항목 | 판단 | 한 줄 근거 |
|---|---|---|
| Q1. API 호출 코드 | **확인(코드)**: 함수는 4개 다 있음, 실제로 쓰이는 건 GET·DELETE뿐 | `saveHighlight`(`lib/api.ts:113-114`)와 `updateHighlight`(`:117-118`)를 import하는 파일이 없다 |
| Q2. 저장 UI | **확인(코드)**: 없음 | 어떤 라우트·컴포넌트에도 하이라이트 생성 버튼/폼/모달이 없다. 조건부 렌더링 때문에 숨은 것이 아니라 **아예 구현되지 않았다** |
| Q2-1. 비슷해 보이는 버튼 | **확인(코드)** | 모음집 카드의 "수정/삭제" 버튼은 hover 전 `opacity-0`이고, 목록이 비어 있으면 카드 자체가 렌더링되지 않는다 |
| Q4. 콘솔/렌더링 에러 | **추측(코드상 성립)** 2건 | ① 모음집 GET이 CORS로 실패하고 조용히 빈 목록이 된다 ② GET이 성공하면 응답 형태가 달라 `HighlightCard`에서 TypeError가 난다 |

**결론:** 버튼이 안 보이는 원인은 렌더링 조건이 아니라 **미구현**이다. 어느 화면을 보고 있었든 저장 버튼은 나오지 않는다.

---

## Q1. `/api/highlights` 호출 코드 — 확인(코드)

### 1) 정의된 API 함수 (`frontend/lib/api.ts`)
| 함수 | 줄 | 메서드 | base URL | 호출하는 곳 |
|---|---|---|---|---|
| `getHighlights` | `:105-110` | GET | `BASE_URL` (직접 호출) | `hooks/useHighlights.ts:13` → `components/highlights/HighlightList.tsx:16` |
| `saveHighlight` | `:113-114` | POST | `BASE_URL` (직접 호출) | **없음** |
| `updateHighlight` | `:117-118` | PUT | `BASE_URL` (직접 호출) | **없음** |
| `deleteHighlight` | `:121-122` | DELETE | `BASE_URL` (직접 호출) | `HighlightList.tsx:5`, `:23` |

- 근거: `frontend/app`, `components`, `hooks`, `lib` 전체를 `saveHighlight|updateHighlight`로 grep했을 때 `lib/api.ts`의 정의 2줄만 나온다.
- 네 함수 모두 `request()`의 세 번째 인자를 넘기지 않으므로 `BASE_URL`(`:12`, 기본값 `http://localhost:8080`)을 쓴다. 검색·저장·캘린더처럼 `PROXY_BASE_URL`(`:16`)을 쓰는 함수와 다르다.

### 2) 호출 흐름 (GET)
`app/highlights/page.tsx:4` `<HighlightList />` → `HighlightList.tsx:16` `useHighlights(year, category)` → `hooks/useHighlights.ts:13` `getHighlights()` → `lib/api.ts:109` `request('/api/highlights?...')`

---

## Q2. 하이라이트 저장 UI — 확인(코드): 미구현

### 1) 존재하는 라우트 (`frontend/app`)
| 라우트 | 파일 | 하이라이트 관련 UI |
|---|---|---|
| `/` | `app/page.tsx` | 없음. 안내 문구만 있다(`:8-10`) |
| `/calendar` | `app/calendar/page.tsx` → `CalendarView` | 없음 |
| `/highlights` | `app/highlights/page.tsx` → `HighlightList` | 조회·삭제만. **추가 버튼 없음** |
| `/search` | `app/search/page.tsx` → `SearchResults` | 없음. "기록하기"는 콘텐츠 저장 버튼이다 |

- **콘텐츠 상세 페이지와 기록 목록 페이지는 라우트 자체가 없다.** `app/` 아래 페이지는 위 4개뿐이다. 그래서 요청서의 예시 화면 중 "콘텐츠 상세 / 기록 목록"은 존재하지 않는 화면이다.
- 사이드바 메뉴도 홈/캘린더/모음집 3개뿐이다(`components/sidebar/Sidebar.tsx:8-12`).

### 2) 모음집 화면(`HighlightList.tsx`)에서 렌더링되는 것
- 헤더 제목과 검색 input(`:39-50`), 카테고리 탭(`:55-69`), 연도 select(`:72-81`), 개수(`:83-85`)
- 목록 3분기(`:89-109`): `loading` → 스켈레톤 / `filtered.length === 0` → "저장된 하이라이트가 없어요" / 그 외 → `HighlightCard`
- **"하이라이트 추가" 버튼, 폼, 모달은 어느 분기에도 없다.**

### 3) 저장 버튼으로 오해할 수 있는 것
- `HighlightCard.tsx:50-63`의 "수정"/"삭제" 버튼
  - 감싸는 div가 `opacity-0 group-hover:opacity-100`(`:50`)이라 **카드에 마우스를 올려야 보인다.**
  - 카드는 목록이 있을 때만 렌더링된다(`HighlightList.tsx:99-108`). 아래 Q4-①처럼 목록이 빈 배열이면 이 버튼들도 볼 수 없다.
  - "수정"은 `onEdit?.(highlight)`(`:52`)를 호출하지만, `HighlightList.tsx:102-106`이 `onEdit`을 넘기지 않으므로 **눌러도 아무 일도 일어나지 않는다.**
- `SearchResultCard.tsx:57-64`의 "기록하기" 버튼과 `SaveContentForm.tsx`의 "저장" 버튼(`:217-223`)
  - 둘 다 **콘텐츠** 저장(`POST /api/contents`, `:76`)이다. 폼 필드는 날짜/상태/평점/한줄평(`:137-199`)이고 하이라이트 입력란은 없다.

### 4) contentId를 화면에서 얻을 수 있는 경로 — 확인(코드)
하이라이트 저장에는 `contentId`가 필수(`HighlightRequestDto.java:19-20`)인데, 지금 프론트에는 저장된 콘텐츠의 id를 들고 있는 화면이 없다.
- `SaveContentForm.tsx:76` `await saveContent(dto)`는 반환값(새 content id, `lib/api.ts:87-89` `request<number>`)을 버린다. `onSaved`에는 검색 결과 `item`만 넘긴다(`:77`, `:12`).
- 검색 결과 타입 `UnifiedSearchResponse`(`types/index.ts:11-20`)에는 `isSaved`만 있고 저장된 content의 id는 없다.
- 백엔드 콘텐츠 조회 API는 `/api/contents/filter`와 `/check`뿐이다(`ContentController.java:40`, `:50`). 단건 조회(`GET /api/contents/{id}`)는 없다.
- 프론트의 `getFilteredContents`(`lib/api.ts:92-97`)는 정의만 있고 호출하는 곳이 없으며, 이것도 직접 호출(`BASE_URL`)이다.

---

## Q4. 브라우저 콘솔/렌더링 에러 가능성

| # | 내용 | 판단 | 근거 |
|---|---|---|---|
| ① | **모음집 GET이 CORS로 실패하고, 화면은 에러 없이 "저장된 하이라이트가 없어요"를 보여 준다** | 추측(코드상 성립, 브라우저 미확인) | `getHighlights`가 `localhost:8080`을 직접 호출(`lib/api.ts:109`)하고, `request()`가 GET에도 `Content-Type: application/json`을 붙여(`:32`) preflight가 생긴다. 백엔드 CORS 설정은 `StatisticsController.java:17` 한 곳뿐이다(`demo/src/main` 전체 grep). 04에서 캘린더로 확인한 것과 같은 경로라서, MVC가 preflight를 403으로 거부할 것으로 본다. `useHighlights.ts:15` `.catch(() => setHighlights([]))`가 에러를 삼키므로 화면에는 빈 상태 문구만 남는다. 콘솔에는 CORS 에러가 찍힐 것이다 |
| ② | **GET이 성공하면 `HighlightCard`에서 TypeError가 난다** | 추측(코드상 성립) | 백엔드 응답은 평평한 `contentId/contentTitle/contentCategory`(`HighlightResponseDto.java:10-21`)이고, 프론트는 중첩 `content` 객체를 기대한다(`types/index.ts:107-121`). 그러면 `HighlightCard.tsx:13` `content.category`에서 `Cannot read properties of undefined (reading 'category')`가 나서 dev 에러 오버레이가 뜬다. 검색어를 입력하면 `HighlightList.tsx:32` `h.content.title`에서도 같은 에러가 난다. 지금은 ① 때문에 이 코드까지 가지 않는다 |
| ③ | 삭제 실패 시 처리되지 않은 Promise 거부 | 추측(코드상 성립) | `HighlightList.tsx:23` `await deleteHighlight(id)`에 try/catch가 없다. ①과 같은 이유(DELETE는 preflight 대상)로 실패하면 콘솔에 `Uncaught (in promise) ApiError`가 찍힐 수 있다. 다만 카드가 렌더링되어야 누를 수 있다 |
| ④ | 06 백엔드 변경이 실행 중인 서버에 반영됐는지 | 미확인 | `GET /api/highlights`(`HighlightController.java:34-39`)는 06 작업으로 워킹 트리에만 있고 커밋되지 않았다(`git status`). 백엔드를 06 이전 빌드로 띄웠다면 GET은 405/404가 된다. 이 경우에도 ①처럼 빈 목록으로 보인다 |

- ①~④는 모두 "저장 버튼이 안 보인다"의 원인이 아니다. 저장 UI는 처음부터 없다(Q2). 다만 저장 UI를 추가해도 **POST가 ①과 같은 이유로 실패하고, 저장 뒤 목록 갱신에서 ②가 터질 수 있다.**

---

## Q3. 저장 UI 추가 위치 선택지 (조사 결과에 따른 정리, 결정은 하지 않음)

| | A. 모음집(`/highlights`)에 "문장 추가" 버튼 + 모달 | B. 콘텐츠 저장 직후 이어서 입력 (`SaveContentForm`) | C. 콘텐츠 상세 페이지 신설 (`/contents/[id]`) |
|---|---|---|---|
| 형태 | 헤더 옆 버튼 → 모달에서 작품 선택 + 문장/위치 입력 | 저장 성공 후 "기억에 남는 문장도 남길까요?" 단계 또는 폼 안 선택 입력란 | 작품 정보 + 그 작품의 하이라이트 목록 + 추가 폼 |
| contentId 확보 | 작품 선택 목록이 필요. `/api/contents/filter`를 프록시로 바꿔 써야 함 | `saveContent` 반환값을 바로 씀(`SaveContentForm.tsx:76`만 고치면 됨) | URL의 id. 단건 조회 API와 작품별 하이라이트 API가 새로 필요 |
| 장점 | 하이라이트 기능이 한 화면에 모인다. 이미 저장한 작품에도 추가할 수 있다. 담당 범위("하이라이트 모음집")와 맞다 | 백엔드 변경이 없다. 작품 선택 UI가 필요 없다 | 사용 흐름이 가장 자연스럽다. 이후 캘린더 날짜 클릭(04의 미구현 항목)과 연결할 수 있다 |
| 단점 | 작품 선택 UI(검색/드롭다운)가 추가로 필요하다 | **이미 저장한 작품에는 추가할 방법이 없다.** 콘텐츠 저장 흐름이 길어진다 | 백엔드 API 2개, 새 라우트, 진입 경로까지 필요해서 세션 1개 범위를 넘는다 |
| 공통 선행 작업 | 하이라이트 API를 프록시로 호출(Q4-①), 응답 타입 정렬(Q4-②) | 같음 | 같음 |

- 개인 판단(추측): 세션당 기능 1개라는 규칙을 고려하면 A가 범위와 효과 면에서 가장 균형이 맞다. B는 A의 보조 진입점으로 나중에 붙일 수 있다.

---

## 미확인 항목 확인 절차 (은서가 직접)
1. **보고 있던 화면**: 주소창의 경로를 확인한다(`/`, `/calendar`, `/highlights`, `/search` 중 하나여야 한다).
2. **Q4-①**: `/highlights`에서 DevTools → Network를 연다. `localhost:8080/api/highlights?`의 OPTIONS 요청이 403인지, Console에 CORS 에러가 있는지 본다.
3. **Q4-④**: 백엔드를 06 변경이 반영된 상태로 다시 띄운 뒤 `curl http://localhost:8080/api/highlights`를 실행한다. 200과 JSON 배열이 오는지, 각 항목이 `contentTitle` 같은 평평한 키인지 확인한다.

## 발견 사항 (범위 밖, 기록만)
1. `HighlightCard`의 "수정" 버튼은 보이지만(hover 시) `onEdit`이 연결되지 않아 동작하지 않는다(`HighlightList.tsx:102-106`). 수정 폼도 없다.
2. 모음집 카테고리 탭의 "영화/드라마" 값은 `MOVIE` 하나다(`lib/constants.ts:54`). 백엔드 `resolveCategories`는 MOVIE를 단일 카테고리로 처리하므로(06 구현), TV로 저장한 드라마는 이 탭에서 빠진다.
3. `useHighlights`는 실패를 빈 배열로 바꿔서(`useHighlights.ts:15`) "요청 실패"와 "하이라이트 없음"을 구분하지 못한다. 캘린더에 대해 backlog에 적힌 것과 같은 문제다.
4. `SaveContentForm`이 `saveContent`의 반환 id를 버린다(`SaveContentForm.tsx:76`). 저장 후 그 기록으로 이동하거나 이어서 작업하는 기능을 만들 때 필요하다(인박스 "저장 성공 후 어디서 볼 수 있는지 안내 없음"과 연결).

## 메모
- 이번 세션은 조사만 했다. 코드/설정 수정, 테스트·dev 서버 실행, 커밋, push는 하지 않았다. 브라우저 동작은 코드로만 추론했다.
- CLAUDE.md의 완료 정리 형식(선택지, 선택 이유)과 복습 질문은 04와 같이 구현 세션에서 작성한다.

---

## 원인 / 근거 / 다음 작업 제안

### 원인
하이라이트 저장 UI가 **구현되어 있지 않다.** 조건부 렌더링으로 숨겨진 것이 아니다. API 함수 `saveHighlight`는 정의만 있고, 어떤 컴포넌트도 호출하지 않는다. 그래서 어느 화면에서도 저장 버튼이 나오지 않는다.

### 근거
- `frontend/lib/api.ts:113-114` `saveHighlight`의 호출처가 0개다(프론트 전체 grep).
- 라우트는 `/`, `/calendar`, `/highlights`, `/search` 4개뿐이다. 콘텐츠 상세나 기록 목록 페이지는 없다.
- `/highlights`(`HighlightList.tsx:36-110`)는 필터와 목록만 렌더링한다. 카드의 수정/삭제 버튼(`HighlightCard.tsx:50-63`)은 hover 때만 보이며, 목록이 비어 있으면 아예 없다.
- 검색 화면의 저장 폼(`SaveContentForm.tsx`)은 콘텐츠 저장이고, 하이라이트 입력란이 없다. 반환된 content id도 버린다(`:76`).
- 덧붙여(추측) 모음집 GET은 직접 호출 + CORS 미설정으로 실패하고 빈 목록으로 보일 가능성이 높다(`lib/api.ts:109`, `useHighlights.ts:15`). 성공하더라도 응답 형태가 달라 `HighlightCard.tsx:13`에서 TypeError가 날 수 있다.

### 다음 작업 제안
1. (선행, 작은 수정) 하이라이트 API 4개를 `PROXY_BASE_URL`로 바꾸고, `HighlightWithContent` 타입과 `HighlightCard`/`HighlightList`를 평평한 DTO에 맞춘다. 이걸 하지 않으면 저장 UI를 붙여도 POST와 목록 갱신이 실패한다.
2. (본 작업) 저장 UI 위치를 Q3의 A/B/C 중에서 정한다. A(모음집에 "문장 추가" 모달)를 고르면 작품 선택용으로 `/api/contents/filter`를 프록시로 함께 써야 한다.
3. 착수 전에 "미확인 항목 확인 절차" 1~3으로 화면 경로, CORS 403, 06 반영 여부를 확인한다.
