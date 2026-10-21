# 10. 하이라이트 수정 UI (카드 "수정" 버튼 → 수정 모달)

- 브랜치: `main` (작업 시작 시점 워킹 트리 clean, 최신 커밋 `ba72778`. 커밋하지 않음)
- 참고: `.claude/tasks/09-highlight-save-ui.md`(추가 모달, 위치 필드 규칙, 전송 규칙), `.claude/decisions.md`(연도 기준 = Content.viewDate, 애니 묶음)

## 배경
`/highlights`에는 추가 모달(`AddHighlightModal`)이 있다(09). 카드에 "수정" 버튼은 보이지만 실제 수정 폼과 연결되어 있지 않다.
백엔드는 바꾸지 않는다. 프론트에서만 해결한다.

## 사전 확인 (사실, 코드 기준)

### 1) 카드 "수정" 버튼의 현재 동작: 눌러도 아무 일도 없음
- `HighlightCard.tsx`는 `onEdit?: (h: HighlightResponseDto) => void` prop을 받고, 버튼이 `onClick={() => onEdit?.(highlight)}`를 부른다.
- 그런데 `HighlightList.tsx`가 카드를 그릴 때 `onDelete`만 넘기고 **`onEdit`은 넘기지 않는다.** 그래서 `onEdit`이 `undefined`이고 옵셔널 호출(`?.`)이라 에러 없이 아무 일도 일어나지 않는다.
- 버튼은 `opacity-0 group-hover:opacity-100`이라 카드에 마우스를 올려야 보인다(삭제 버튼과 같음).

### 2) `updateHighlight` 시그니처 vs 백엔드 `PUT /api/highlights/{id}`: 일치
- 프론트: `updateHighlight(id: number, dto: HighlightRequestDto)` → `PUT /api/highlights/${id}`, 응답 `request<number>` (`lib/api.ts:116-118`). 정의만 있고 호출처는 없었다.
- 백엔드: `HighlightController.update(@PathVariable Long id, @Valid @RequestBody HighlightRequestDto)` → `ResponseEntity<Long>`(id 그대로). 저장과 **같은 DTO, 같은 @Valid 규칙**을 쓴다.

| 필드 | 백엔드 `HighlightRequestDto` | 프론트 `HighlightRequestDto` |
|---|---|---|
| `text` | `@NotBlank`, `@Size(max = 1000)` | `text: string` |
| `contentId` | `@NotNull Long` | `contentId: number` |
| `page` | `@Min(1)`, null 허용 | `page?: number` |
| `season` / `episode` | 검증 없음, null 허용 | `season?` / `episode?: number` |
| `timestamp` | 검증 없음(형식 강제 X), null 허용 | `timestamp?: string` |

- 호출 주소는 `BASE_URL`(8080 직접). 08 전역 CORS가 `/api/**`에 적용되어 있다(허용 메서드에 PUT 포함 여부는 아래 검증 결과 참고). 호출 방식은 바꾸지 않는다.

### 3) 백엔드 update 동작
- `HighlightService.updateHighlight`(`:53-68`): `findById(id)` 없으면 `IllegalArgumentException("해당 하이라이트가 없습니다. id=...")` → 400 `{ status, message }`.
- **`contentId`는 쓰지 않는다.** `@NotNull`이라 보내야는 하지만, 존재 여부도 확인하지 않고 연관관계도 바꾸지 않는다 → **작품 변경 불가.**
- `Highlight.update(text, page, season, episode, timestamp)`는 **5개를 통째로 덮어쓴다.** 키를 빼고 보내면 Jackson이 `null`로 채우므로 그 필드는 **null이 된다**(부분 수정 아님).
- `BaseTimeEntity.createdAt`은 `@Column(updatable = false)`, `modifiedAt`은 `@LastModifiedDate`. 목록 정렬은 `createdAt desc, id desc`(`HighlightService.java:84`).

### 4) 수정 후 목록 위치/노출에 영향을 주는 것
- 연도 필터 기준: 부모 `Content.viewDate` / 탭 필터 기준: 부모 `Content.category` (decisions.md). 둘 다 작품 쪽 값이고 수정으로 작품이 바뀌지 않는다 → **수정해도 현재 탭/연도에서 빠지거나 다른 탭으로 옮겨가지 않는다.**
- 정렬 기준 `createdAt`은 수정으로 바뀌지 않는다 → **수정해도 목록 순서가 그대로다**(맨 위로 올라가지 않음).
- 예외: 헤더 검색창은 클라이언트 필터(`text`/`contentTitle` 포함 여부)다. 검색어가 들어간 상태에서 문장을 고쳐 검색어가 빠지면, 재조회 후 그 카드가 목록에서 사라진다. 의도된 동작(검색 조건에 안 맞음)으로 보고 시나리오에 적는다.

### 5) 검증 도구 현황 (09와 같음)
- `lint` 스크립트·ESLint·프론트 테스트 도구 없음 → 도입하지 않고 `tsc --noEmit` + `next build` + 은서 확인 시나리오.

## 목표
카드의 "수정" 버튼으로 기존 하이라이트의 문장과 위치를 고치고, 저장하면 목록에 바로 반영된다.

## 설계 방향
- **새 컴포넌트를 만들지 않고 `AddHighlightModal`을 `mode: 'create' | 'edit'`로 확장한다.** 문장 입력·카운터·위치 필드 규칙·검증(`buildDto`)·에러 표시가 생성과 완전히 같고, 다른 점은 "작품 선택 vs 작품 고정"과 "호출 API"뿐이다.
  - props는 판별 유니온: `{ mode?: 'create' }` 또는 `{ mode: 'edit'; highlight: HighlightResponseDto }`. 수정 모드에서만 `highlight`가 필수라는 걸 타입이 강제한다.
- 수정 모드
  - 기존 값 전체(text/page/season/episode/timestamp)를 미리 채운다. `null`은 빈 칸.
  - 작품은 읽기 전용(배지 + 제목). 작품 목록(`getFilteredContents`)은 부르지 않는다.
  - 요청 시 기존 `contentId`를 그대로 보낸다. `contentId`가 `null`인 하이라이트(FK NOT NULL 아님)는 보낼 수 없으므로 안내 문구를 띄우고 저장 버튼을 막는다.
  - 호출: `updateHighlight(highlight.id, dto)`. 성공 → 모달 닫기 + `refetch()`. 실패 → `toErrorMessages`로 모달 안 표시(생성과 동일).
- 위치 필드 규칙은 09 그대로: BOOK → page / MOVIE·ANIME_MOVIE → timestamp / TV·ANIME_TVA·ANIME → season+episode. 보이는 필드만 담고 빈 칸은 키를 뺀다.
  - 백엔드가 통째로 덮어쓰므로 **"빈 칸으로 저장 = 그 값을 지움(null)"** 이 된다. 별도 처리 없이 이 의미를 그대로 쓴다.
  - 카테고리가 `null`이라 위치 필드가 하나도 안 보이는 경우에는, 보이지 않는 값이 조용히 지워지지 않도록 기존 위치 값을 그대로 다시 보낸다.

## DoD
- [ ] 카드 "수정" → 수정 모달 (제목 "하이라이트 수정")
- [ ] 기존 값 전체 미리 채움, 작품 읽기 전용(배지/제목)
- [ ] 기존 contentId 그대로 전송, contentId null이면 저장 막고 안내
- [ ] 카테고리별 위치 필드, 빈 칸 → null로 지워짐
- [ ] 성공 시 닫기 + 목록 재조회, 실패 시 서버 메시지(두 형식) 모달 안 표시
- [ ] 수정 후 탭/연도 필터에서 위치·노출 확인 (코드 근거 + 시나리오)
- [ ] `tsc --noEmit`, `npm run build` 통과
- [ ] 은서 확인 시나리오 작성

## 수정 허용 범위
- `frontend/components/highlights/AddHighlightModal.tsx` (mode 확장)
- `frontend/components/highlights/HighlightList.tsx` (`onEdit` 연결, 수정 모달 상태)
- 문서: 이 파일, `.claude/backlog.md`(추가만)

## 범위 밖
백엔드 전부(작품 변경, 부분 수정, timestamp 서버 검증 등), 탭 "영화/드라마" 매핑, API 호출 방식 통일, 토스트, ESLint·테스트 도구 도입, 컴포넌트 이름 변경(`AddHighlightModal` → `HighlightFormModal` 등)

---

## 구현 내용
- `frontend/components/highlights/AddHighlightModal.tsx` (mode 확장, 새 파일 없음)
  - `Props`(`:11`)를 판별 유니온으로 바꿨다. `mode`를 안 주면 지금처럼 create, `mode: 'edit'`이면 `highlight`가 필수다. 기존 호출부(`<AddHighlightModal onClose onSaved />`)는 고치지 않아도 그대로 컴파일된다.
  - 미리 채우기: 각 `useState` 초기값을 `highlight`에서 가져온다(`toInput`, `:51`, `null` → `''`). 모달은 열 때마다 새로 마운트되므로(`editing && <… />`) 초기값만으로 충분하다.
  - 수정 모드는 작품 목록을 부르지 않는다(`:72`, `loadingContents` 초기값도 `false`, `:57`).
  - `selected`(`:102`): 수정 모드는 하이라이트의 `contentId/contentTitle/contentCategory`로 만든 고정 값, 추가 모드는 select에서 고른 값. 그래서 `kind`(위치 필드 종류)와 `buildDto`의 `contentId`가 두 모드에서 같은 코드로 나온다. 수정 모드는 결과적으로 **기존 contentId를 그대로** 보낸다.
  - `contentId`가 `null`이면 `missingContent`(`:109`) → 안내 문구 + 저장 버튼 비활성.
  - 카테고리가 `null`이라 위치 칸이 하나도 안 보일 때만 기존 위치 값을 그대로 다시 담는다(`:135`). 백엔드가 통째로 덮어써서, 이걸 안 하면 사용자가 본 적도 없는 값이 지워진다.
  - 저장: `isEdit`이면 `updateHighlight(highlight.id, dto)`, 아니면 `saveHighlight(dto)`(`:155`). 에러 처리(`toErrorMessages` → `role="alert"` 목록)는 그대로 공유한다.
  - 화면: 제목 "하이라이트 수정", 작품 칸은 배지+제목 읽기 전용 박스(`:211`)와 "작품은 바꿀 수 없어요. 비운 위치 칸은 저장하면 지워져요." 안내.
- `frontend/components/highlights/HighlightList.tsx`
  - `editing` 상태(`:17`) 추가, 카드에 `onEdit={setEditing}`(`:119`)을 넘긴다. 이게 원래 빠져 있던 연결이다.
  - `editing`이 있으면 `mode="edit"` 모달(`:136`). 저장 성공 → `setEditing(null)` + `refetch()`.
- `HighlightCard.tsx`는 고치지 않았다. `onEdit(highlight)`로 카드 데이터 전체를 넘기는 구조가 이미 있었다.

## 검증 결과
### 실행한 것
- [x] `npx tsc --noEmit`: 통과(exit 0). 처음엔 2건 실패: `selected.category`가 `Category | null`이 되면서 추가 모드 배지의 `CATEGORY_BADGE[selected.category]`가 null 인덱스 에러 → `selected?.category &&`로 가드.
- [x] `npm run build`: 통과(exit 0, `/highlights` 5.66 kB → 6.1 kB). 09와 같이 3000번에 `next dev`가 떠 있어서 소스를 scratch 폴더로 복사(node_modules는 junction)해 빌드했고, 끝난 뒤 junction과 복사본을 지웠다. `tsc`가 만든 `frontend/tsconfig.tsbuildinfo`도 지웠다.
- [ ] `npm run lint`: 스크립트 없음(09와 동일, backlog에 이미 있음).
- [x] 실행 중인 백엔드(8080)에 **데이터를 바꾸지 않는 요청만** 보냈다(curl).
  - `OPTIONS /api/highlights/1` (Origin `http://localhost:3000`, PUT) → 200, `Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS`. 직접 호출 PUT이 CORS에 막히지 않는다.
  - `PUT /api/highlights/999999999` 빈 text + page 0 + contentId 없음 → 400 `{"contentId":…,"page":"페이지는 1 이상이어야 합니다.","text":…}` (@Valid 맵 형식)
  - `PUT /api/highlights/999999999` 정상 본문 → 400 `{"status":400,"message":"해당 하이라이트가 없습니다. id=999999999"}` (ErrorResponse 형식). 두 형식 모두 `toErrorMessages`가 처리하는 모양이다.
  - `GET /api/highlights` → 2건(id 105 `페스트` BOOK, id 106 `귀멸의 칼날: 환락의 거리 결전편` ANIME_MOVIE). 둘 다 위치 값이 전부 null.
- **정상 수정(PUT 성공)은 실행하지 않았다.** 실제 DB(Supabase)의 은서 데이터라서다. 아래 시나리오로 은서가 확인한다.

### 수정 후 목록 위치/노출 (코드 근거)
- 탭: 백엔드 필터가 `Content.category`를 본다. 작품이 안 바뀌므로 수정 전후 같은 탭에 있다.
- 연도: `Content.viewDate` 기준. 마찬가지로 안 바뀐다.
- 순서: `createdAt desc, id desc`이고 `createdAt`은 `updatable = false`. 수정해도 순서가 그대로다(카드가 맨 위로 튀지 않음). `modifiedAt`은 갱신되지만 정렬·응답에 쓰지 않는다.
- 클라이언트 검색어: 유일한 예외. 검색어가 빠지도록 문장을 고치면 재조회 후 카드가 사라진다(검색 조건 불일치, 의도된 동작).

### 은서 확인 시나리오 (백엔드 8080 + `npm run dev` 3000)
1. **진입**: `/highlights`에서 카드에 마우스를 올리면 "수정" 버튼이 보인다. 누르면 "하이라이트 수정" 모달이 뜬다. (전에는 아무 일도 없었다)
2. **미리 채움**: `페스트`(책) 카드 수정 → 작품 칸에 `책` 배지 + `페스트`가 회색 박스로 보이고 고를 수 없다. 문장 칸에 기존 문장, 카운터 `n / 1000`, "페이지 (선택)" 칸만 보인다(지금은 page가 null이라 빈 칸).
   - 애니 극장판 카드(`귀멸의 칼날…`)는 "시간 (선택)"만 보인다.
3. **저장 → 목록 반영**: 책 카드에 페이지 `123`을 넣고 문장 끝에 글자를 하나 추가해 저장 → 모달이 닫히고 같은 자리의 카드에 `p.123`과 바뀐 문장이 보인다. 카드 순서와 날짜(작성일)는 그대로다.
   - DevTools Network: `PUT /api/highlights/105`, 본문 `{"text":"…","contentId":78,"page":123}`.
4. **위치 필드 비우기**: 같은 카드를 다시 수정 → 페이지 칸에 `123`이 미리 채워져 있다. 지우고 저장 → 카드에서 `p.123`이 사라진다. 본문에 `page` 키가 없고, 백엔드가 통째로 덮어써서 DB 값이 null이 된다.
5. **검증/에러**: 문장을 다 지우고 저장 → "문장을 입력해주세요." / 페이지 `0` → 1 이상 안내 / 시간 `1:2` → 형식 안내. 모달을 연 채로 백엔드를 끄고 저장 → "서버에 연결할 수 없습니다…"가 모달 안에 뜨고 모달은 닫히지 않는다.
6. **탭/연도 필터**: "책" 탭 + 2026년을 선택한 상태에서 책 카드를 수정 → 저장 후에도 그 탭/연도에 같은 위치로 남는다. "애니" 탭에서 극장판 카드를 수정해도 마찬가지.
   - 검색창에 `페스트`를 넣고 그 카드 문장만 고치면 그대로 남는다(제목으로 걸림). 검색창에 문장 일부(예: `시신`)를 넣고 그 단어를 지우도록 고치면 저장 후 카드가 목록에서 빠진다(검색 조건 불일치, 정상).
7. **취소**: 문장을 고치다가 ESC/바깥 클릭/취소 → 저장되지 않고, 다시 열면 원래 값이 채워져 있다.
8. **추가 모달 회귀**: 헤더 "하이라이트 추가"는 전처럼 작품 select가 있고 저장된다(09 시나리오 1~4와 같음).

---

## 변경 파일 (커밋하지 않음)
- 수정: `frontend/components/highlights/AddHighlightModal.tsx`, `frontend/components/highlights/HighlightList.tsx`
- 신규: `.claude/tasks/10-highlight-edit-ui.md` (이 파일)
- 수정: `.claude/backlog.md` (발견 사항 추가만)
- 수정하지 않음: 백엔드 전부, `HighlightCard.tsx`, `lib/api.ts`, `types/index.ts`, `package.json`

## 발견 사항 (범위 밖, 기록만)
1. **PUT도 `contentId`가 `@NotNull`인데 서비스는 쓰지 않는다.** 저장과 같은 `HighlightRequestDto`를 공유해서다. 프론트는 의미 없는 값을 "필수로" 보내야 하고, 다른 contentId를 보내도 400 없이 200이 나와 작품이 바뀐 것처럼 오해할 수 있다. 수정 전용 DTO(contentId 없음)로 나누거나, 작품 변경을 지원할지 결정이 필요하다. → backlog
2. **수정은 부분 수정이 아니라 전체 덮어쓰기다.** 위치 칸을 비우면 null이 된다. 지금 UI는 이걸 "비우면 지움"으로 안내하고 그대로 쓴다. 다만 카테고리와 안 맞는 위치 값(예: 책인데 timestamp)이 DB에 있으면, 수정 모달에서 보이지 않는 그 값은 저장 시 지워진다. 지금 저장 UI(09)는 그런 조합을 만들지 않고, 현재 DB 하이라이트 2건은 위치가 전부 null이라 영향 없음. → 이 파일에만 기록
3. **timestamp 형식을 서버가 검증하지 않는다.** 09 이전에 API로 `1:2` 같은 값이 들어갔다면 수정 모달에 그대로 채워지고, 프론트 검증(`HH:MM:SS`) 때문에 고치기 전에는 저장할 수 없다. 현재 데이터에는 해당 없음. → 이 파일에만 기록
4. **카드의 수정/삭제 버튼이 hover에서만 보인다**(`opacity-0 group-hover:opacity-100`). 터치 기기에서는 버튼이 안 보여 수정·삭제를 못 한다. → backlog
5. `AddHighlightModal`이 이제 추가·수정을 함께 하지만 이름은 "Add"다. 이름 변경은 import 경로가 바뀌는 리팩터라 이번 범위 밖으로 뒀다. → backlog

---

## 정리 (트러블슈팅용)

### 1. 문제 상황
하이라이트 카드에 "수정" 버튼과 `onEdit` prop, `updateHighlight` API 함수, 백엔드 `PUT /api/highlights/{id}`가 모두 있었지만, 목록이 카드에 `onEdit`을 넘기지 않아 버튼을 눌러도 아무 일도 없었다. 게다가 백엔드 수정은 contentId를 무시하고 5개 필드를 통째로 덮어써서, 폼이 무엇을 보내느냐에 따라 값이 의도치 않게 지워질 수 있었다.

### 2. 고려한 선택지
- A. 기존 `AddHighlightModal`을 `mode: 'create' | 'edit'`로 확장
- B. `EditHighlightModal`을 새로 만들기 (추가 모달 복사 후 작품 선택 제거)
- C. 카드 안에서 바로 고치는 인라인 편집 (textarea로 전환)

### 3. 선택 이유 (트레이드오프)
- **A**를 골랐다. 두 폼의 차이는 "작품 선택 vs 고정"과 "POST vs PUT" 두 가지뿐이다. 문장 검증, 1000자 카운터, 카테고리별 위치 필드, `buildDto`의 "보이는 필드만 전송", 에러 표시가 모두 같다. B로 복사하면 이 규칙이 두 군데에 생기고, 한쪽만 고치면 생성과 수정의 검증이 어긋난다. 비용은 컴포넌트 안에 `isEdit` 분기가 생긴다는 점이다. props를 판별 유니온으로 만들어 "수정이면 `highlight` 필수"를 타입으로 강제했다. 그리고 `selected`를 두 모드에서 같은 모양으로 맞춰 분기를 작품 칸 렌더링·데이터 로딩·API 호출 세 곳으로 줄였다.
- C는 카드가 좁아 위치 필드(시즌/화 등)까지 넣기 어렵고, 저장 중/에러 상태를 카드마다 들고 있어야 한다.
- 빈 칸 처리: 백엔드가 통째로 덮어쓰므로 "빈 칸 = 지움"을 그대로 쓰고 모달에 안내 문구를 넣었다. "빈 칸이면 기존 값 유지"로 만들면 사용자가 위치를 지울 방법이 없어진다.

### 4. 막혔던 부분과 해결
- **`selected` 타입을 넓히자 추가 모드에서 컴파일 에러가 났다.** 수정 모드의 작품은 `contentCategory: Category | null`이라, 공통 `selected.category`도 nullable이 됐다. 추가 모드 배지가 `CATEGORY_BADGE[selected.category]`로 null을 인덱스로 쓰게 되어 `tsc`가 잡았다. `selected?.category &&`로 가드했다. 응답 타입을 `| null`로 정직하게 적어 둔 덕분(09)에 런타임 전에 걸렸다.
- **"덮어쓰기"라서 생기는 숨은 삭제.** 카테고리가 null이면 위치 칸이 하나도 안 보이는데, 기존 규칙대로 "보이는 필드만" 보내면 사용자가 본 적도 없는 값이 null이 된다. 그 경우에만 기존 값을 그대로 다시 보내게 했다.
- **실제 DB라 PUT 성공은 실행하지 않았다.** 대신 데이터를 안 바꾸는 요청(preflight, @Valid 실패, 없는 id)으로 CORS와 두 에러 형식을 확인했고, 성공 경로는 시나리오로 넘겼다.

### 개념 메모
- **판별 유니온(discriminated union)**: `{ mode?: 'create' } | { mode: 'edit'; highlight: … }`처럼 공통 필드(`mode`) 값으로 나머지 모양이 정해지는 타입이다. `mode="edit"`인데 `highlight`를 빼면 컴파일 에러가 나서, "수정인데 원본이 없는" 상태를 코드로 만들 수 없게 된다.
- **PUT 전체 교체**: 이 API는 받은 본문으로 필드를 통째로 바꾼다. JSON에서 키를 빼면 Jackson이 DTO 필드를 null로 두고, 엔티티 `update`가 그 null을 그대로 대입한다. 일부만 바꾸려면 PATCH + "키가 있을 때만 반영" 같은 별도 규칙이 필요하다.

---

## 복습 질문 (프론트엔드 → 답 포함)
<details>
<summary>1. 페이지가 <code>123</code>인 책 하이라이트를 수정 모달에서 열고, 페이지 칸을 비운 채 저장했다. 요청 본문은 어떤 모양이고, 저장 후 DB의 page는 무엇인가? 어느 코드 두 곳이 그 결과를 만드는가?</summary>

답: 본문은 `{ "text": "…", "contentId": 78 }`로 `page` 키가 없다. 프론트 `buildDto`에서 `parsePositiveInt('')`가 `undefined`를 돌려줘 `location.page`를 담지 않기 때문이다(`AddHighlightModal.tsx`). 백엔드는 Jackson이 빠진 키를 `null`로 두고, `HighlightService.updateHighlight` → `Highlight.update(text, page, …)`가 `this.page = page`로 null을 그대로 대입한다. 그래서 DB page는 null이 되고 카드의 `p.123`이 사라진다. 저장(POST)에서는 "키 빼기 = 값 없음"이었는데, 수정(PUT 전체 교체)에서는 "키 빼기 = 지우기"가 된다.

</details>

<details>
<summary>2. 수정 모달에서 다른 작품으로 바꿀 수 없게 만든 이유는? 만약 프론트가 <code>contentId</code>를 다른 작품 id로 바꿔 보내면 어떤 응답이 오고 실제로 무엇이 바뀌는가?</summary>

답: 백엔드 `updateHighlight`가 `requestDto.getContentId()`를 아예 읽지 않는다. 그래서 다른 id를 보내도 @Valid(`@NotNull`)만 통과하면 200과 같은 id가 오고, 하이라이트는 원래 작품에 그대로 붙어 있다. UI에서 작품을 고를 수 있게 하면 "바꿨는데 안 바뀌는" 화면이 된다. 그래서 수정 모드는 작품을 읽기 전용으로 보여 주고 기존 contentId를 그대로 보낸다. 작품 변경을 지원하려면 백엔드 수정이 필요하다(발견 사항 1).

</details>

<details>
<summary>3. (기존 코드 연결) "애니" 탭 + 2026년 필터를 켠 상태에서 애니 극장판 하이라이트의 문장을 고쳐 저장했다. 저장 후 그 카드는 목록 어디에 보이는가? 백엔드 어떤 코드들이 그걸 보장하는가?</summary>

답: 같은 탭, 같은 연도, 같은 순서 자리에 바뀐 문장으로 보인다. (1) 탭 필터는 `HighlightService.resolveCategories`가 ANIME를 ANIME+ANIME_MOVIE+ANIME_TVA로 펼쳐 부모 `Content.category`로 거르고, (2) 연도는 `HighlightSpecifications.viewYear`가 부모 `Content.viewDate` 범위로 거른다. 수정은 작품을 바꾸지 않으니 둘 다 결과가 같다. (3) 정렬은 `createdAt desc, id desc`인데 `BaseTimeEntity.createdAt`이 `@Column(updatable = false)`라 수정해도 안 바뀐다. 바뀌는 건 `modifiedAt`뿐이고 정렬·응답에 쓰지 않는다. 그래서 `refetch()` 후에도 카드가 맨 위로 튀지 않는다.

</details>
