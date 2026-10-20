# 05. 사이드바 미니 캘린더 점 표시 수정

- 브랜치: `fix/calendar`
- 참고: `.claude/tasks/04-calendar-investigation.md` (원인 A·B·C1), `.claude/decisions.md` 2026-10-20 캘린더 결정 3건

## 목표
사이드바 미니 캘린더에 저장한 날짜의 점이 찍히게 한다. 04 조사에서 확인한 세 원인을 각각 고친다.
1. userId null (B): `Content.userId`에 `@Builder.Default`를 붙여 1L을 유지한다.
2. CORS (A): 캘린더 호출을 검색/저장이 쓰는 Next 프록시 방식으로 통일한다. 백엔드 CORS 설정은 건드리지 않는다.
3. 오늘 날짜 점 (C1): 오늘 칸에도 점을 그리고, 오늘 하이라이트와 시각적으로 겹치지 않게 한다.

## 수정 허용 범위
- demo: `Content.java`, 새 테스트 파일 1개
- frontend: `lib/api.ts`의 캘린더 호출, 프록시 설정/라우트(검색/저장과 같은 위치), `components/sidebar/MiniCalendar.tsx`

## 범위 밖
요청 실패 시 에러 표시(useCalendar), 메인 캘린더 날짜 클릭, 하이라이트/통계의 CORS, GET /api/highlights, 통계 NPE, 인증/JWT, 기존 DB 행의 user_id 보정

---

## 구현 내용

### 1. userId (백엔드)
- `Content.java:31-34` `userId`에 `@Builder.Default`를 붙였다. 주석은 `// 임시 (JWT 병합 시 교체)`로 바꿨다.
- 같은 클래스 `highlights` 필드(`:59-61`)와 같은 방식이다. 필드 초기값은 그대로 두고 그 위에 `@Builder.Default`만 얹었다.
- 새 테스트 `demo/src/test/java/book_log/demo/dto/request/ContentRequestDtoTest.java` (서버/DB 불필요)
  - `toEntityKeepsDefaultUserId`: `ContentRequestDto.toEntity()`로 만든 Content의 `getUserId()`가 `1L`인지 확인한다.
  - `toEntityKeepsDefaultHighlights`: userId 수정 후에도 `highlights`가 null이 아닌 빈 리스트인지 확인한다(기존 동작 회귀 방지).
  - 수정 전 `Content.java`(HEAD)로 잠시 바꿔 돌리면 userId 테스트만 실패한다(`AssertionError at ContentRequestDtoTest.java:36`). 확인 후 수정본으로 복원했다.

#### @Builder를 쓰는 다른 클래스 조사 (보고만, 수정 없음)
| 클래스 | 초기값이 있는 필드 | 같은 문제 |
|---|---|---|
| `domain/Highlight` | 없음 | 없음 |
| `dto/request/ContentRequestDto` | 없음 | 없음 |
| `dto/request/HighlightRequestDto` | 없음 | 없음 |
| `dto/response/SearchResponse` | 없음 | 없음 |
| `dto/response/StatisticsResponse` (내부 `ActivityComparison` 포함) | 없음 | 없음 |
| `dto/response/UnifiedSearchResponse` (`toBuilder = true`) | 없음 | 없음 |

`@Builder`와 필드 초기값이 함께 있는 곳은 `Content.userId`, `Content.highlights` 두 군데뿐이다. 이번 수정으로 둘 다 `@Builder.Default`를 갖게 됐다.

### 2. CORS (프론트)
- 검색/저장의 프록시 방식(기존):
  - `next.config.js:4-10` rewrites가 `/api/:path*`를 `${NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'}/api/:path*`로 넘긴다.
  - `lib/api.ts`에서는 `request()`의 세 번째 인자로 `PROXY_BASE_URL = ''`를 넘긴다. 그러면 `/api/...` 상대 경로, 즉 같은 출처로 호출된다(`searchContents :83`, `saveContent :89`).
- rewrites의 `source`가 이미 `/api/:path*` 와일드카드라 `/api/calendar/activities`도 포함된다. 그래서 **`next.config.js`는 수정하지 않았다.** 별도 라우트 파일(`app/api/...`)도 만들지 않았다.
- `lib/api.ts:71` `getCalendarActivities`에 `undefined, PROXY_BASE_URL`을 넘기도록 바꿨다. 저장 호출과 같은 형태다. `:14` 주석의 "검색/저장"은 "검색/저장/캘린더"로 고쳤다.
- 같은 출처 요청이 되므로 브라우저가 CORS preflight를 보내지 않는다. `Content-Type: application/json` 헤더(`:32`)는 그대로 붙지만, 같은 출처에서는 문제가 되지 않는다.

#### 영향 범위
- `getCalendarActivities`를 부르는 곳은 `hooks/useCalendar.ts`의 두 훅뿐이다(`:13` `useMiniCalendar`, `:32` `useCalendar`).
  - `useMiniCalendar` → `components/sidebar/MiniCalendar.tsx` → `Sidebar` → `app/layout.tsx`. 모든 페이지의 사이드바가 영향을 받는다.
  - `useCalendar` → `components/calendar/CalendarView.tsx`(`/calendar`)의 날짜 그리드
- 바꾸지 않은 것:
  - 메인 캘린더 상단 리캡은 `getMonthlyStatistics`(`api.ts:75-76`)를 쓴다. 지금도 8080을 직접 호출한다(통계는 `@CrossOrigin("*")`이라 원래 동작한다).
  - 하이라이트, `filter`/`check`도 직접 호출 그대로다(백로그).

### 3. 오늘 날짜 점 (프론트)
- 오늘 칸의 기존 스타일(`MiniCalendar.tsx:80-81`): `rounded-md` 정사각형에 배경을 꽉 채운다(`bg-neutral-900`, 다크 모드는 `bg-white`). 숫자는 흰색(다크 모드는 검정) `font-semibold`다.
- 변경(`:86-94`): `!todayFlag` 조건을 지웠다. 점의 위치(`absolute bottom-[2px]` 가운데)와 크기(3px)는 다른 날과 똑같고, **오늘 칸에서만 색을 바꾼다.**
  - 라이트 모드: 어두운 배경 위에 `bg-violet-300`(밝은 보라)
  - 다크 모드: 흰 배경 위에 `bg-violet-600`(진한 보라)
  - 다른 날: 기존 `bg-violet-500` 유지
- 위치와 크기를 그대로 둔 이유: 한 주의 점들이 같은 높이에 나란히 보여야 한다. 점의 의미("기록 있음 = 보라 점")도 유지된다.
- 색만 바꾼 이유: 기존 `violet-500`을 오늘 배경 위에 그대로 그리면 라이트 모드의 거의 검은 배경에서 대비가 약하다. 숫자는 칸 가운데, 점은 아래 2px에 있어서 원래 날짜들과 마찬가지로 숫자와 겹치지 않는다.

---

## 검증 결과

### 서버 없이 확인한 것
- [x] `./gradlew clean test`: **60개 전부 통과**(실패 0, 에러 0, 스킵 0). 기존 58개 + 새 2개(`ContentRequestDtoTest`)
- [x] 새 userId 테스트가 수정 전 코드에서 실패하는 것 확인(빨간불 → 초록불)
- [x] 프론트 `npx tsc --noEmit` 통과
  - 이 명령이 만든 `frontend/tsconfig.tsbuildinfo`(`incremental: true` 산출물, git 미추적)는 삭제했다.
- [ ] `next build`: 실행하지 않았다. 개발 서버가 떠 있으면 같은 `.next` 폴더를 덮어써서 개발 서버를 깨뜨릴 수 있기 때문이다.
- [ ] ESLint: 프로젝트에 설정 파일이 없어 실행하지 않았다(03과 동일).

### 서버가 필요한 것 (은서 확인 시나리오)
백엔드(8080)와 `npm run dev`(Next)를 모두 띄운 상태에서 확인한다. 프록시는 `next dev`/`next start`에서만 동작한다.

1. **주소창** `http://localhost:8080/api/calendar/activities?year=2026&month=10`
   - 수정 후 **새로 저장한** 기록의 날짜가 `days`에 `{"day": 20, "thumbnail": null, "count": null}` 형태로 들어 있어야 한다.
   - 수정 전에 저장한 행은 user_id가 여전히 NULL이라 나오지 않는다(보정은 범위 밖, decisions 2026-10-20 "SQL로 직접 보정"). 나오지 않는 날짜가 있으면 4번 SQL로 그 행의 user_id를 확인한다.
   - 백엔드를 재시작해야 `@Builder.Default`가 반영된다.
2. **사이드바**에서 저장한 날짜에 보라 점이 찍히는지 본다.
   - 오늘(20일) 칸: 검은 배경 아래쪽에 밝은 보라 점. 다크 모드에서는 흰 배경에 진한 보라 점
   - 다른 날: 기존과 같은 보라 점
   - 메인 캘린더(`/calendar`) 날짜 그리드에도 같은 날짜에 점이 생기는지 함께 본다.
3. **네트워크 탭**에서 `activities` 요청을 확인한다.
   - Request URL이 `http://localhost:3000/api/calendar/activities?year=2026&month=10`(페이지와 같은 출처, 8080 아님)이고, 상태가 200이어야 한다.
   - 그 앞에 `OPTIONS` 요청이 더 이상 없어야 한다.
4. **DB** (Supabase SQL Editor)
   ```sql
   SELECT id, title, view_date, user_id, created_at
   FROM content
   ORDER BY id DESC
   LIMIT 5;
   ```
   수정 후 새로 저장한 행의 `user_id`가 `1`이어야 한다.

---

## 변경 파일
- `demo/src/main/java/book_log/demo/domain/Content.java` (수정)
- `demo/src/test/java/book_log/demo/dto/request/ContentRequestDtoTest.java` (신규)
- `frontend/lib/api.ts` (수정: 캘린더 호출, 프록시 주석 1줄)
- `frontend/components/sidebar/MiniCalendar.tsx` (수정)
- `.claude/tasks/05-calendar-fix.md` (신규, 이 파일)
- 수정하지 않음: `next.config.js` (기존 와일드카드 rewrites로 충분)

## 발견 사항 (범위 밖, 기록만)
- **수정 전에 저장한 행은 여전히 user_id NULL이다.** 그래서 이번 수정만으로는 과거 기록의 점이 나오지 않는다. decisions에 따라 SQL로 따로 보정해야 한다.
- `lib/api.ts` 주석 `:15` "(백엔드 @CrossOrigin은 StatisticsController에만 있음)"은 여전히 맞다. 다만 통계, 하이라이트, filter/check는 직접 호출로 남아 있어서 호출 방식이 두 갈래다(백로그).
- `request()`는 GET에도 `Content-Type: application/json`을 붙인다(`api.ts:32`). 프록시를 쓰면 무해하지만, 직접 호출로 남은 GET은 이 헤더 때문에 preflight가 생긴다.
- `Content`의 no-args 생성자와 빌더 모두 userId 1L을 넣게 됐다. 하지만 `findActiveDays`, `CalendarController.java:35`의 `1L`, 엔티티 기본값이 같은 "임시 사용자 1" 가정을 세 군데에서 따로 갖고 있다. JWT 병합 때 함께 바꿔야 한다.

---

## 정리 (트러블슈팅용)

### 1. 문제 상황
사이드바 미니 캘린더에 저장한 날짜의 점이 찍히지 않았다. 원인은 세 가지가 겹쳐 있었다.
- 캘린더 API만 Next 프록시를 거치지 않고 8080을 직접 호출했다. 그런데 백엔드에 CORS 설정이 없어서 preflight가 403으로 막혔다.
- `@Builder`가 `userId = 1L` 초기값을 무시해서 저장 행의 user_id가 NULL이었고, `user_id = 1` 조회에서 빠졌다.
- 사이드바는 `!todayFlag` 조건 때문에 오늘 날짜 점을 일부러 숨겼다.

### 2. 고려한 선택지
- CORS: (a) 백엔드에 전역 CORS 설정(`CorsConfigurationSource`, `WebMvcConfigurer`) 추가 (b) 컨트롤러에 `@CrossOrigin` 추가 (c) 검색/저장이 쓰는 Next 프록시로 통일
- userId: (a) `@Builder.Default` 추가 (b) `toEntity()`에서 `.userId(1L)` 명시 (c) DB 컬럼 DEFAULT + `@DynamicInsert`
- 오늘 점: (a) 오늘 칸 점을 칸 밖(아래)으로 빼기 (b) 같은 위치에 대비되는 색으로 그리기

### 3. 선택 이유 (트레이드오프)
- CORS (c): 이미 검증된 방식을 재사용해서 코드 변경이 인자 1개로 끝난다. 같은 출처 요청이라 preflight 자체가 사라진다. 대신 Next 서버를 거치는 한 단계(hop)가 늘고, 호출 방식(프록시/직접)이 아직 두 갈래로 남는다.
- userId (a): 기본값을 엔티티 한 곳에 두면 빌더를 쓰는 모든 경로(테스트, 다른 DTO 포함)에 적용된다. (b)는 `toEntity()`를 거치지 않는 빌더 호출에서 또 빠질 수 있다. (c)는 DB와 엔티티 두 곳을 맞춰야 한다.
- 오늘 점 (b): 칸 크기와 그리드 높이가 그대로 유지되고, 한 줄의 점들이 같은 높이에 정렬된다. 색 대비만 맞추면 되므로 변경이 가장 작다.

### 4. 막혔던 부분과 해결
- 수정 전 코드에서 테스트가 실패하는지 보려면 엔티티를 되돌려야 했다. 수정본을 scratchpad에 백업하고, HEAD 버전으로 잠시 바꿔 실패(빨간불)를 확인한 뒤 복원했다.
- "프록시에 캘린더 경로 추가"를 하려고 설정을 열어 보니, rewrites가 이미 `/api/:path*` 와일드카드였다. 설정은 그대로 두고 호출 쪽(`PROXY_BASE_URL`)만 바꿨다. 경로가 막혀 있었던 게 아니라 호출 함수가 프록시 경로를 고르지 않은 것이 원인이었다.

### 개념 메모
- **`@Builder`와 필드 초기값**: Lombok 빌더는 필드마다 자기 변수를 따로 갖고, `build()` 때 그 값을 전체 생성자에 넘긴다. 클래스의 `= 1L`은 빌더 변수에 복사되지 않으므로, 설정하지 않으면 null이 들어간다. `@Builder.Default`를 붙이면 "설정 안 됐으면 초기값 사용" 로직(`$default$필드명`)이 생긴다.
- **프록시로 CORS가 사라지는 이유**: CORS는 브라우저가 다른 출처로 요청할 때만 거는 검사다. 브라우저는 같은 출처의 `/api/...`로 요청하고, 다른 출처(8080)로 넘기는 일은 Next 서버가 서버끼리 처리한다. 서버 간 요청에는 CORS가 적용되지 않는다.

---

## 복습 질문
1. `@Builder.Default` 없이 `private Long userId = 1L;`만 있을 때 `Content.builder().build().getUserId()`는 무엇을 반환하나? JPA가 DB에서 조회할 때 쓰는 no-args 생성자 경로와는 어떻게 다른가?
2. 캘린더 GET 요청은 왜 "단순 요청"이 아니어서 preflight가 생겼나? 프록시로 바꾸면 preflight가 사라지는 이유를 "출처" 관점에서 설명해 보자.
3. (기존 코드 연결) 통계 API(`StatisticsController`)는 8080을 직접 부르는데도 왜 동작했나? 통계 쿼리에는 user_id 조건이 없다는 점이 04 조사의 증상 3("저장한 것들이 들어와 있긴 하다")과 어떻게 연결되나?
