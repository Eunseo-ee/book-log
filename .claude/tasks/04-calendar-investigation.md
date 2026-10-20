# 04. 캘린더 표시/클릭 안 됨 원인 조사

- 브랜치: `fix/calendar`
- 참고: `.claude/inbox.md` 2026-10-20 캘린더 항목 2건

## 목표
사이드바 미니 캘린더와 메인 캘린더에 저장한 날짜가 표시되지 않고, 클릭도 되지 않는 원인을 코드로 조사한다.
가설마다 "코드상 성립 / 불성립 / 미확인"으로 판단하고 근거를 파일:줄로 남긴다. 해결 방안은 적지 않는다.

## 증상 (은서 확인)
1. 사이드바 미니 캘린더에 저장한 날짜의 점이 안 찍힌다.
2. 네트워크 탭에서 `GET /api/calendar/activities?year=2026&month=10`이 CORS 오류, preflight(OPTIONS) 403으로 실패한다.
3. 메인 캘린더에 저장한 기록이 들어와 있지만 클릭이 안 된다.
4. 메인 캘린더에서 저장한 날짜에 아무것도 표시되지 않는다.

## 검증할 가설
- A. CORS: 프론트가 백엔드를 브라우저에서 직접 호출하고, 백엔드가 preflight를 막는다.
- B. userId null: `Content.userId = 1L` 초기값이 `@Builder`에서 무시되어 user_id가 null로 저장되고, `findActiveDays`의 `user_id = :userId`에 걸리지 않는다.
- C. 그 밖의 원인 후보 (날짜/월 변환, 응답 DTO, 프론트 해석, viewDate null 등)

## 범위
- 수정 허용: 이 파일, `.claude/inbox.md`(캘린더 항목 아래 "분류:" 줄 추가)
- 범위 밖: 모든 코드/설정 파일 수정, 커밋, push, 해결 방안 제안

---

## 조사 결과 요약
| 가설 | 판단 | 한 줄 근거 |
|---|---|---|
| A. CORS | **코드상 성립** | 두 캘린더 모두 `localhost:8080`을 브라우저에서 직접 호출. CalendarController에 CORS 설정이 없어서 Spring MVC가 preflight를 403으로 거부 |
| B. userId null | **코드상 성립** (DB 값은 미확인) | `@Builder`가 `userId = 1L` 초기값을 무시함(바이트코드로 확인). `toEntity()`·`saveContent()` 어디서도 userId를 넣지 않음 |
| C. 기타 | 후보 7건 검토, 성립 3건 | 그중 C1 "사이드바는 오늘 날짜 점을 숨김"은 A·B가 모두 해결돼도 증상 1을 남기는 원인 |

요청이 A 단계에서 먼저 실패하므로 브라우저에서는 B의 영향이 아직 드러나지 않는다. A가 사라져도 B 때문에 `days`가 빈 배열로 올 수 있다(코드 기준).

---

## A. CORS — 코드상 성립

### 1) 사이드바와 메인 캘린더의 호출 방식
둘 다 **브라우저에서 백엔드를 직접 호출**한다(클라이언트 컴포넌트에서 호출하며, Next 프록시도 서버 컴포넌트도 거치지 않는다).
- `frontend/components/sidebar/MiniCalendar.tsx:1` `'use client'` → `:14` `useMiniCalendar(year, month)`
- `frontend/components/calendar/CalendarView.tsx:1` `'use client'` → `:19` `useCalendar(year, month)`
  - `frontend/app/calendar/page.tsx:4`는 `<CalendarView />`를 렌더링만 하고 서버에서 fetch하지 않는다.
- `frontend/hooks/useCalendar.ts:1` `'use client'`이며, `:13`과 `:32`의 두 훅이 모두 `getCalendarActivities(year, month)`를 호출한다.
- `frontend/lib/api.ts:70-71` `getCalendarActivities`는 `request()`의 세 번째 인자 `baseUrl`을 넘기지 않으므로 기본값 `BASE_URL`(`:30`)을 쓴다.
- `frontend/lib/api.ts:12` `BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'`. `frontend/`에는 `.env.local` 없이 `.env.local.example`만 있으므로 요청 URL은 `http://localhost:8080/api/calendar/activities...`가 된다. 페이지 출처(Next dev 기본값은 `localhost:3000`이며 실제 포트는 미확인)와 다른 출처다.
- 반면 `searchContents`(`:83`)와 `saveContent`(`:89`)는 `PROXY_BASE_URL = ''`(`:16`)를 넘겨 `next.config.js:4-10`의 rewrites(`/api/:path*` → Spring)를 거친다. **캘린더는 이 프록시 경로를 쓰지 않는다.**

### 2) GET인데도 preflight가 생기는 이유
- `frontend/lib/api.ts:32` `request()`는 GET을 포함한 모든 요청에 `headers: { 'Content-Type': 'application/json' }`를 붙인다.
- CORS 단순 요청으로 인정되는 Content-Type에 `application/json`은 없다. 그래서 브라우저는 GET을 보내기 전에 `OPTIONS` preflight를 먼저 보낸다.

### 3) 백엔드 CORS 설정 위치
- `@CrossOrigin`: `StatisticsController.java:17`의 `@CrossOrigin(origins = "*")` **한 곳뿐이다.**
- `SecurityConfig`의 cors 설정: **없다.** `config/SecurityConfig.java:17-29`에는 csrf, authorizeHttpRequests, headers 설정만 있다.
- `WebMvcConfigurer`, `addCorsMappings`, `CorsConfigurationSource` 빈: **없다.** `demo/src` 전체를 grep했을 때 cors 관련 매치는 StatisticsController 하나다.

### 4) preflight 403은 Security 필터가 아니라 Spring MVC에서 나온다
- Security 필터 체인은 OPTIONS를 막지 않는다. `SecurityConfig.java:22-24`가 `anyRequest().permitAll()`이고, cors 설정이 없어 Security의 CorsFilter도 체인에 들어가지 않는다.
  - 근거: spring-security-config 6.4.2 `HttpSecurityConfiguration.java:134-137`. `UrlBasedCorsConfigurationSource` 빈이 있을 때만 `http.cors()`를 자동 적용하는데, 이 프로젝트에는 그런 빈이 없다.
- preflight는 Security를 통과해 DispatcherServlet까지 들어가고, 핸들러에 CORS 설정이 없으면 Spring MVC가 거부한다.
  - 근거: spring-web 6.2.1 `DefaultCorsProcessor.java:102-110`. `config == null`이면서 preflight이면 `rejectRequest()`를 호출하고, 이 메서드는 상태 403과 본문 "Invalid CORS request"를 쓴다(`:115-118` 주석). 응답에 `Access-Control-Allow-Origin` 헤더가 없으므로 브라우저는 이를 "CORS 오류"로 표시한다.
  - 버전은 Boot 3.4.1(`demo/build.gradle:3`) 기준이며, Gradle 캐시의 sources jar로 확인했다.
- 따라서 증상 2의 "OPTIONS 403"은 **CalendarController에 CORS 설정이 없다는 사실**과 맞는다. 실제 응답 본문은 미확인이다(확인 절차 2).

### 5) 컨트롤러별 CORS 허용 현황
| 컨트롤러 | CORS 허용 | 프론트 호출 경로 (`lib/api.ts`) |
|---|---|---|
| CalendarController | ✗ 없음 | `:70-71` 직접 호출 → **preflight 403 대상** |
| StatisticsController | ✓ `@CrossOrigin("*")` (`:17`) | `:75-76` 직접 호출 → preflight 통과 |
| ContentController | ✗ 없음 | 저장(`:89`)은 프록시. `filter`(`:92-97`)와 `check`(`:100-101`)는 직접 호출 |
| HighlightController | ✗ 없음 | `:105-122` 전부 직접 호출 |
| SearchController | ✗ 없음 | `:83` 프록시 |

---

## B. userId null — 코드상 성립 (DB 실제 값은 미확인)

- `domain/Content.java:11` 클래스에 `@Builder`가 있고, `:32-33` `@Column(name = "user_id") private Long userId = 1L;`에는 **`@Builder.Default`가 없다.**
- 같은 클래스의 `:58-60` `highlights` 필드에는 **`@Builder.Default`가 있다.**
- 컴파일된 클래스(`demo/build/classes/.../Content*.class`, 2026-10-20 17:34 빌드)를 `javap`로 확인했다.
  - `highlights`: `Content$ContentBuilder`에 `highlights$set`과 `highlights$value` 필드가 있다. `build()`는 값이 설정되지 않았으면 `Content.$default$highlights()`를 호출한다.
  - `userId`: 빌더에는 일반 필드 `private Long userId`만 있고, `build()`는 이 값을 그대로 넘긴다. `$default$userId`는 존재하지 않는다.
  - `1L` 대입(`lconst_1` → `Long.valueOf`)은 `protected Content()`(no-args 생성자) 안에만 있다. 이 생성자는 JPA가 조회할 때 쓰는 경로이고, 빌더 경로에서는 실행되지 않는다.
- `dto/request/ContentRequestDto.java:46-58` `toEntity()`는 `.userId(...)`를 호출하지 않으므로 빌더의 userId는 null로 남는다.
- `service/ContentService.java:64-67`은 `toEntity()` 결과를 그대로 `save()`하며, 중간에 userId를 넣는 코드가 없다.
- `Content`에 `@DynamicInsert`가 없으므로 Hibernate는 INSERT할 때 `user_id`에 NULL을 명시적으로 넣는다. 그래서 DB 컬럼에 DEFAULT가 있어도 적용되지 않는다(Hibernate 기본 동작 기준).
- NULL 삽입을 막는 제약도 코드에는 없다. `@Column`에 `nullable = false`가 없고, `application.yml:23`이 `ddl-auto: update`다. 실제 DB 제약은 미확인이다.
- `repository/ContentRepository.java:57-69` `findActiveDays`의 조건은 `WHERE c.user_id = :userId`이고, `CalendarController.java:35`가 `userId = 1L`을 넘긴다. SQL에서 `NULL = 1`은 참이 아니므로 user_id가 NULL인 행은 결과에서 빠진다.
- 기존 `CalendarServiceTest`는 리포지토리를 Mock으로 바꿔 쓴다(`:24-25`, `:36`). 따라서 이 쿼리와 user_id 조건은 테스트로 검증되지 않는다.

### 확인용 SQL (PostgreSQL / Supabase, 실행하지 않음)
```sql
-- ① 최근 저장 행의 user_id, view_date 확인
SELECT id, title, category, view_date, user_id, created_at
FROM content
ORDER BY id DESC
LIMIT 20;

-- ② user_id / view_date NULL 분포
SELECT COUNT(*)                                  AS total,
       COUNT(*) FILTER (WHERE user_id IS NULL)   AS user_id_null,
       COUNT(*) FILTER (WHERE user_id = 1)       AS user_id_1,
       COUNT(*) FILTER (WHERE view_date IS NULL) AS view_date_null
FROM content;

-- ③ findActiveDays와 같은 조건 (2026년 10월, userId=1)
SELECT DISTINCT CAST(EXTRACT(DAY FROM c.view_date) AS INTEGER) AS day
FROM content c
WHERE c.user_id = 1
  AND EXTRACT(YEAR FROM c.view_date) = 2026
  AND EXTRACT(MONTH FROM c.view_date) = 10
ORDER BY 1;

-- ④ ③에서 user_id 조건만 뺀 것 (③과 다르면 user_id 때문에 빠진 것)
SELECT DISTINCT CAST(EXTRACT(DAY FROM c.view_date) AS INTEGER) AS day
FROM content c
WHERE EXTRACT(YEAR FROM c.view_date) = 2026
  AND EXTRACT(MONTH FROM c.view_date) = 10
ORDER BY 1;

-- ⑤ 컬럼 제약/기본값
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'content' AND column_name IN ('user_id', 'view_date');
```

---

## C. 그 밖의 원인 후보

| # | 후보 | 판단 | 근거 |
|---|---|---|---|
| C1 | **사이드바는 오늘 날짜의 점을 그리지 않는다** | **코드상 성립** | `MiniCalendar.tsx:87` `{hasRecord && !todayFlag && (...)}` 조건 때문에 오늘 칸에는 점이 없고, `:80-81`의 검은 배경만 표시된다. 저장 폼의 viewDate 기본값이 오늘이므로(`SaveContentForm.tsx:20-24` `todayString()`, `:31`), 오늘(10/20) 날짜로 저장한 기록은 A·B가 해결돼도 사이드바에 점이 보이지 않는다. 메인 캘린더(`CalendarView.tsx:143-144`)는 오늘 칸에도 점을 그린다 |
| C2 | 실패를 빈 화면으로 처리한다 | 코드상 성립 (원인은 아니고 증상을 가리는 요인) | `useCalendar.ts:18` `.catch(() => setRecordedDays(new Set()))`, `:34` `.catch(() => setData(null))`. CORS 실패도 "기록 없음"과 같은 화면이 되며 에러 표시가 없다 |
| C3 | 통계와 캘린더의 조회 조건이 다르다 | 코드상 성립 (증상 3의 "들어와 있음"과 관련 가능) | 통계 쿼리(`ContentRepository.java:74-98`)에는 `user_id` 조건이 없고, 통계 컨트롤러에는 `@CrossOrigin`이 있다(`StatisticsController.java:17`). 그래서 메인 캘린더 상단 리캡(`CalendarView.tsx:68-95`, "이번 달 기록 N개")은 보이는데 날짜 그리드만 비는 상태가 코드상 가능하다. 은서가 본 "들어와 있음"이 이 리캡 카드인지는 미확인 |
| C4 | viewDate가 null인 행 | 새로 저장하는 행은 코드상 불성립, 기존 행은 미확인 | `ContentRequestDto.java:35-36` `@NotNull`, `ContentController.java:28` `@Valid`, 프론트 `SaveContentForm.tsx:49`에서 모두 검증한다. 이 검증이 생기기 전에 들어간 행은 SQL ②로 확인한다 |
| C5 | 날짜/월 변환 (0-based, 시간대) | 코드상 불성립 | 프론트 month는 `getMonth() + 1`(`MiniCalendar.tsx:12`, `CalendarView.tsx:16`), 백엔드는 `@Min(1) @Max(12)`(`CalendarController.java:31`)와 `EXTRACT(MONTH)`로 양쪽 다 1~12다. 저장 날짜는 toISOString이 아닌 로컬 기준 `YYYY-MM-DD` 문자열이고(`SaveContentForm.tsx:19-24`), 엔티티 필드는 `LocalDate`(`Content.java:29`)라 시간대 변환이 없다 |
| C6 | 응답 DTO 필드와 프론트 타입 불일치 | 코드상 불성립 | `CalendarResponse.java:5-18` record의 `year/month/days`, `day/thumbnail/count`가 `types/index.ts:48-57`의 이름과 같다. 프론트는 `d.day`로 읽는다(`useCalendar.ts:16`, `CalendarView.tsx:38`) |
| C7 | native 쿼리 결과 타입 (`EXTRACT` → Integer) | 미확인 | `ContentRepository.java:58`에서 `CAST(... AS INTEGER)`로 맞추고 있어 코드상 문제는 보이지 않는다. 실제 동작은 확인 절차 4로 본다 |

---

## 증상 3, 4 (메인 캘린더)

### 데이터 출처
- 날짜 그리드: `CalendarView.tsx:19` → `useCalendar.ts:32` → `GET {BASE_URL}/api/calendar/activities?year={year}&month={month}`(`api.ts:70-71`). 사이드바와 **같은 엔드포인트를 같은 방식(직접 호출)으로** 부르므로 A·B의 영향을 똑같이 받는다.
- 상단 리캡: `CalendarView.tsx:22-26` → `GET {BASE_URL}/api/statistics?year&month`(`api.ts:75-76`). CORS가 허용된 컨트롤러다(C3).

### 클릭 핸들러 — 없음 (미구현)
- 날짜 칸 `<div>`(`CalendarView.tsx:124-131`)에 `onClick`이 없고 `Link`로 감싸여 있지도 않다. 기록이 있는 칸에는 `cursor-pointer`와 hover 스타일만 붙는다(`:129`).
- 파일 어디에도 선택한 날짜를 담는 상태(`useState`), 상세 패널, 라우팅이 없다. 날짜별 상세를 보여줄 화면도 없다(`frontend/app/`에는 `calendar`, `highlights`, `search`, 루트만 있다).
- 날짜로 콘텐츠를 조회하는 API도 없다. `ContentController.java:50-58`의 `/filter`는 year와 category만 받고, `CalendarResponse.DayActivity`에는 contentId가 없다(`CalendarResponse.java:14-18`, 주석 "나중에 thumbnail, contentId 등을 추가").
- 판단: 핸들러가 있는데 동작하지 않는 버그가 아니라 **기능 자체가 구현되지 않았다.** 덧붙이면 지금은 A 때문에 `activity`가 항상 비어 있어서 `cursor-pointer`도 붙지 않는다(코드 기준).

### 날짜 칸 표시 방식과 thumbnail/count null의 영향
- `CalendarView.tsx:38`에서 `dayMap = new Map(data?.days.map(d => [d.day, d]) ?? [])`을 만들고, `:120`에서 `activity = dayMap.get(d)`로 꺼낸다.
- `:137-145`: `thumbnail`이 있으면 `<img>`를 그리고, thumbnail 없이 `activity`만 있으면 보라색 점(`w-[6px]`)을 그린다. 둘 다 없으면 아무것도 그리지 않는다.
- `:148` `activity?.count && activity.count > 1 && (...)`: count가 null이면 식 전체가 `null`이 되어 배지를 그리지 않을 뿐, 화면 오류는 나지 않는다.
- 백엔드는 항상 `new DayActivity(day, null, null)`을 만들므로(`CalendarService.java:27`) thumbnail과 count는 늘 null이다.
- 결론: thumbnail/count가 null이어도 **`days`에 날짜가 들어 있으면 점은 표시된다.** null 때문에 달라지는 것은 썸네일과 개수 배지가 나오지 않는다는 점뿐이다. 증상 4("아무것도 표시 안 됨")는 `days`가 비었거나 요청이 실패한 경우(A, B)에만 생긴다.

---

## 미확인 항목 확인 절차 (은서가 직접)

**브라우저**
1. 프론트 페이지 주소창에서 출처를 확인한다(예: `http://localhost:3000`).
2. 개발자도구 → Network에서 실패한 `OPTIONS /api/calendar/activities` 행을 클릭한다.
   - Request Headers에 `Origin`, `Access-Control-Request-Method: GET`, `Access-Control-Request-Headers: content-type`이 있는지 본다(A-2 확인).
   - Response 본문이 `Invalid CORS request`인지, Response Headers에 `Access-Control-Allow-Origin`이 없는지 본다(A-4 확인).
3. 같은 화면의 `/api/statistics` 요청과 비교한다. 이쪽 preflight가 200이고 `Access-Control-Allow-Origin: *`가 있으면 A의 판단과 맞다.
4. 새 탭 주소창에 `http://localhost:8080/api/calendar/activities?year=2026&month=10`을 직접 입력한다. 주소창에서 여는 요청에는 CORS가 적용되지 않는다.
   - `"days": []`(빈 배열)이면 B 또는 C4 쪽이다. 아래 SQL로 이어서 확인한다.
   - `days`에 저장한 날짜가 들어 있으면 B는 영향이 없고 A만 원인이다.
   - 들어 있는 날짜가 오늘(20)뿐이면, A가 해결돼도 사이드바에는 C1 때문에 점이 보이지 않는다.
5. 메인 캘린더에서 "들어와 있다"고 본 것이 상단 리캡 카드("이번 달 기록 N개")인지 날짜 칸 안의 무언가인지 기록한다(C3 확인).

**SQL** (Supabase SQL Editor에서 위 B 섹션 쿼리 실행)
6. ①로 최근 저장 행의 `user_id`와 `view_date`를 본다. `user_id`가 비어 있으면 B가 성립한다.
7. ②로 NULL 건수를 센다. `view_date_null`도 함께 보면 C4를 확인할 수 있다.
8. ③과 ④의 결과를 비교한다. ④에만 나오는 날짜는 user_id 조건 때문에 빠진 것이다.
9. ⑤로 `user_id` 컬럼의 `is_nullable`과 `column_default`를 확인한다.

---

## 발견 사항 (범위 밖, 기록만)
- `frontend/lib/api.ts:15` 주석은 "검색/저장은 프록시를 거친다"는 내용이고, 실제로 캘린더, 통계, 콘텐츠 filter/check, 하이라이트는 모두 직접 호출한다. 이 중 하이라이트(`:105-122`)와 `filter`/`check`도 CORS 설정이 없는 컨트롤러를 부르므로 같은 preflight 403을 받을 것이다(코드 기준, 미확인).
- `frontend/lib/api.ts:105-110`이 호출하는 `GET /api/highlights` 엔드포인트가 `HighlightController.java`에 없다. POST/PUT/DELETE만 있고, 주석에 "백엔드 추가 예정"이라고 되어 있다.
- `StatisticsController.java:30-35`에서 `month`는 `required = false`인 `Integer`인데, null 체크보다 먼저 `month < 1`을 비교한다. month 없이 호출하면 NPE가 난다. 프론트는 항상 month를 보내므로 지금은 해당되지 않는다.
- 통계 쿼리(`ContentRepository.java:74-98`)와 `getFilteredContents`는 user_id로 거르지 않고, 캘린더만 user_id로 거른다.
- `ContentService.isAlreadySaved`(`:30-32`)도 user_id를 보지 않는다. decisions 2026-10-19의 "isSaved의 userId 미반영(JWT 병합 시)"과 같은 맥락이다.
- `CalendarResponse.DayActivity.of()`(`CalendarResponse.java:20-22`)는 정의만 있고 쓰는 곳이 없다. `CalendarService.java:27`은 생성자를 직접 호출한다.

## 메모
- 이번 세션은 조사만 했다. 코드/설정 수정, 테스트 실행, 커밋, push는 하지 않았다. 바이트코드와 Spring 소스는 기존 빌드 산출물과 Gradle 캐시의 jar를 읽기만 했다(Spring 소스는 scratchpad에 풀어서 확인).
- CLAUDE.md의 완료 정리 형식(선택지, 선택 이유)과 복습 질문은 해결 방안을 정하는 수정 세션에서 작성한다.
