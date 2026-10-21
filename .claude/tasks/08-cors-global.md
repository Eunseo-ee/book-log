# 08. 백엔드 CORS 전역 설정

- 브랜치: `main` (작업 시작 시점 브랜치. 06 작업이 커밋되지 않은 상태로 워킹 트리에 있음. 커밋하지 않음)
- 참고: `.claude/tasks/05-calendar-fix.md` CORS 선택지, `.claude/tasks/07-highlight-save-ui-investigation.md` Q4-①, `.claude/decisions.md` 2026-10-20 캘린더 CORS 결정

## 배경 (사실 확인, 코드 기준)
- 백엔드 CORS 설정은 `StatisticsController.java:17` `@CrossOrigin(origins = "*")` 한 곳뿐이다(`demo/src/main` 전체 grep). `SecurityConfig`에는 `.cors()`가 없다.
- **`frontend/next.config.js`에 rewrites 프록시가 이미 있다.** `/api/:path*` → `${NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'}/api/:path*` (`:4-11`). 와일드카드라 모든 `/api/**`가 대상이다.
- 프론트가 API를 부르는 주소는 함수마다 다르다(`frontend/lib/api.ts`). `request()`의 세 번째 인자로 갈린다(`:30`).
  - `BASE_URL`(`:12`) = `NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'` → 8080 직접 호출(다른 출처, CORS 대상)
  - `PROXY_BASE_URL`(`:16`) = `''` → 같은 출처 `/api/...` → Next rewrites가 8080으로 넘김(CORS 무관)

| 함수 | 엔드포인트 | 호출 주소 |
|---|---|---|
| `getCalendarActivities` (`:70-71`) | GET `/api/calendar/activities` | 프록시 |
| `searchContents` (`:80-83`) | GET `/api/search` | 프록시 |
| `saveContent` (`:87-88`) | POST `/api/contents` | 프록시 |
| `getMonthlyStatistics` (`:74-75`) | GET `/api/statistics` | **직접** (백엔드 `@CrossOrigin`으로 통과) |
| `getFilteredContents` (`:91-96`) | GET `/api/contents/filter` | **직접** (CORS 설정 없음) |
| `checkDuplicate` (`:99-100`) | GET `/api/contents/check` | **직접** (CORS 설정 없음) |
| `getHighlights` / `saveHighlight` / `updateHighlight` / `deleteHighlight` (`:104-121`) | `/api/highlights/**` | **직접** (CORS 설정 없음) |

- `request()`는 GET에도 `Content-Type: application/json`을 붙인다(`:32`). 그래서 직접 호출하는 GET도 단순 요청이 아니고 preflight(OPTIONS)가 먼저 나간다.
- 정리: "다른 엔드포인트가 막힐 수 있다"는 정확히는 **직접 호출로 남은 contents/filter·check와 highlights 4개**가 해당한다. search·calendar·contents 저장은 지금도 프록시라 막히지 않는다.

## 목표
모든 `/api/**`에 대해 허용 origin의 요청(GET, POST, PUT, DELETE, OPTIONS preflight 포함)을 백엔드가 통과시킨다. 허용 origin은 `"*"`가 아니라 설정값으로 관리한다.

## 설계 방향 (확정)
- 선택지: (A) 전역 CORS 설정 (B) 컨트롤러마다 `@CrossOrigin` (C) Next.js rewrites 프록시
- **A를 선택.** 한 곳에서 관리하고, 배포 시 origin만 바꾸면 된다. B는 누락 위험이 있다(지금 실제로 발생: Statistics에만 있음). C는 개발 환경(`next dev`/`next start`)에서만 해결되고 배포 구조에 종속된다.
- 2026-10-20 결정("캘린더 CORS는 Next 프록시로 통일, 백엔드 전역 CORS 설정은 하지 않음")을 이번 결정이 대체한다. 프론트 호출 방식(프록시/직접)은 이번에 건드리지 않는다. 프록시 호출은 같은 출처라 전역 설정과 충돌하지 않는다.
- 허용 origin: 프로퍼티 `app.cors.allowed-origins`, `@Value` 기본값 `http://localhost:3000`. `application.yml`에 의존하지 않는다(gitignore 대상, 내용 출력·커밋 금지). 쉼표로 여러 개 지정 가능.
- 스프링 시큐리티를 쓰므로 MVC 설정만으로는 preflight가 시큐리티 필터에서 막힐 수 있다. `SecurityConfig`에서 `.cors()`를 켜고, 같은 `CorsConfigurationSource` 빈을 쓰게 한다.
- `StatisticsController`의 `@CrossOrigin(origins = "*")`는 제거한다(전역 설정으로 대체). 이유는 decisions.md에 한 줄 기록.

## 테스트
- `CorsConfigTest` (신규, `@WebMvcTest` + `@Import(SecurityConfig)`, 서비스는 `@MockitoBean`)
  - 기존 리포지토리 테스트처럼 DB가 필요 없는 웹 슬라이스라 Supabase에 붙지 않는다. 기존 테스트 파일은 수정하지 않는다.
  - 검증: 허용 origin preflight(OPTIONS, GET/POST/PUT/DELETE) 200 + `Access-Control-Allow-Origin` / 허용하지 않은 origin preflight 403 + 헤더 없음 / 실제 요청도 같은 규칙 / `@CrossOrigin` 제거 후 statistics도 같은 규칙 / 프로퍼티로 origin 교체
- `./gradlew test` 전체 통과 확인

## DoD
- [ ] 허용 origin의 `/api/**` preflight가 200이고 `Access-Control-Allow-Origin`이 그 origin으로 온다
- [ ] 허용하지 않은 origin은 403이고 `Access-Control-Allow-Origin`이 없다
- [ ] 허용 origin은 `app.cors.allowed-origins`(기본 `http://localhost:3000`)로 바꿀 수 있다
- [ ] `StatisticsController`의 `@CrossOrigin` 제거, decisions.md 기록
- [ ] 새 테스트 통과, `./gradlew test` 전체 통과

## 수정 허용 범위
- demo main: `config/SecurityConfig.java`, `controller/StatisticsController.java`(`@CrossOrigin` 제거만)
- demo test: `config/CorsConfigTest.java`(신규)
- 문서: 이 파일, `.claude/decisions.md`(추가만), 발견 사항 기록 위치(기존 관례: 이 파일 + backlog)

## 범위 밖
프론트 호출 방식(프록시/직접) 정리, `next.config.js`, 하이라이트 타입 불일치, StatisticsController month NPE, 인증/JWT·`allowCredentials`, `application.yml`, 기존 테스트 파일

---

## 구현 내용
- `demo/src/main/java/book_log/demo/config/SecurityConfig.java`
  - `@Value("${app.cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins`: 프로퍼티가 없으면 기본값을 쓴다. 쉼표로 여러 개를 넣으면 배열로 나뉜다. `application.yml`은 건드리지 않았다(파일에 `cors` 문자열이 0건인 것만 개수로 확인).
  - `corsConfigurationSource()` 빈(신규): `/api/**`에 허용 origin, 메서드 `GET, POST, PUT, DELETE, OPTIONS`, 헤더 `*`, `maxAge 3600`을 등록한다. `allowCredentials`는 켜지 않았다(쿠키/인증 없음).
  - `filterChain`에 `.cors(Customizer.withDefaults())`를 추가했다. 이름이 `corsConfigurationSource`인 빈을 찾아 시큐리티 필터 체인 안에 `CorsFilter`로 넣는다. preflight는 DispatcherServlet까지 가지 않고 이 필터가 응답한다.
  - MVC `addCorsMappings`는 따로 만들지 않았다. 설정은 빈 하나에만 있고, 실제 요청에도 필터가 헤더를 붙인다. MVC 쪽에서 같은 내용을 한 번 더 선언하면 두 곳이 어긋날 수 있다.
- `demo/src/main/java/book_log/demo/controller/StatisticsController.java`: `@CrossOrigin(origins = "*")`와 import를 지웠다. 다른 코드는 그대로다.

## 검증 결과
### 서버 없이 확인한 것
- [x] `./gradlew test --rerun-tasks`: **86개 전부 통과**(실패 0, 에러 0, 스킵 0). 기존 75개 + 새 11개
  - `CorsConfigTest` 11개: 허용 origin preflight GET/POST/PUT/DELETE(200 + Allow-Origin + Allow-Methods) / 비허용 origin preflight 403 + 헤더 없음 / 실제 GET 허용·거절 / statistics 허용·거절 / 슬라이스에 없는 `/api/contents` preflight 200 / 프로퍼티로 origin 2개 지정 시 둘 다 허용, 나머지 거절
- [x] `CorsConfigTest` 로그에 DB 연결(`HikariPool`, `Database version`)이 0건. 웹 슬라이스라 Supabase에 붙지 않는다.
- [x] 빨간불 확인: `.cors(Customizer.withDefaults())` 한 줄만 주석 처리하면 11개 중 9개가 실패한다. preflight는 전부 403, 허용 origin 실제 요청은 Allow-Origin 헤더가 null, 비허용 origin 실제 요청은 200(그냥 통과). **`CorsConfigurationSource` 빈이 있어도 `.cors()`가 없으면 아무 데도 적용되지 않는다.** 확인 후 원래대로 복원했다.
- [ ] 실제 브라우저에서 확인: 하지 않았다.
- 참고(기존 문제, 수정 안 함): 이번 실행에서도 `DemoApplicationTests`, `HighlightRepositoryTest`는 `Database version: 17.6`(Supabase)에 붙었다. backlog에 이미 있다.

### 서버가 필요한 것 (은서 확인 시나리오)
백엔드(8080)와 `npm run dev`(3000)를 띄운 상태에서 확인한다.
1. `/highlights` 화면에서 DevTools → Network: `localhost:8080/api/highlights?` 앞의 OPTIONS가 **200**이고, 응답 헤더에 `Access-Control-Allow-Origin: http://localhost:3000`이 있다. Console에 CORS 에러가 없다. (07 Q4-①이 해소됐는지 확인. 이후 화면에서 07 Q4-② TypeError가 날 수 있는데, 이건 CORS와 별개 문제다)
2. 메인 화면 통계 카드가 이전처럼 나온다(`@CrossOrigin` 제거 후에도 전역 설정으로 통과).
3. 터미널에서 거절 확인:
   `curl -i -X OPTIONS http://localhost:8080/api/highlights -H "Origin: http://evil.example.com" -H "Access-Control-Request-Method: GET"` → `403`, 본문 `Invalid CORS request`
4. origin 교체 확인(선택): `./gradlew bootRun --args='--app.cors.allowed-origins=http://localhost:3001'`로 띄우면 3번 명령의 Origin을 `http://localhost:3001`로 바꿨을 때 200, `http://localhost:3000`이면 403.

---

## 변경 파일 (커밋하지 않음)
- 수정: `demo/src/main/java/book_log/demo/config/SecurityConfig.java`
- 수정: `demo/src/main/java/book_log/demo/controller/StatisticsController.java` (`@CrossOrigin` 제거만)
- 신규: `demo/src/test/java/book_log/demo/config/CorsConfigTest.java`
- 신규: `.claude/tasks/08-cors-global.md` (이 파일)
- 수정: `.claude/decisions.md` (2026-10-21 한 줄 추가. 9번째 줄 끝에 붙어 있던 `# 확정된 설계 결정` 제목은 새 줄 추가 때문에 줄바꿈만 넣어 분리했다)
- 수정: `.claude/backlog.md` (발견 사항 3줄 추가만)
- 수정하지 않음: 프론트 전부, `next.config.js`, `application.yml`, 기존 테스트 파일. 06 작업 파일들은 작업 전부터 워킹 트리에 있었고 건드리지 않았다.

## 발견 사항 (범위 밖, 기록만)
1. **프론트 호출 방식이 두 갈래로 남아 있다.** 프록시(검색/캘린더/콘텐츠 저장)와 8080 직접(통계/contents filter·check/하이라이트 4개). 전역 CORS로 둘 다 동작하지만, 어느 쪽으로 통일할지는 정하지 않았다. `lib/api.ts:14-15` 주석("백엔드 @CrossOrigin은 StatisticsController에만 있음")은 이제 사실과 다르다. → backlog
2. **허용 origin이 정확히 `http://localhost:3000` 하나다.** 3000 포트가 사용 중이라 `next dev`가 3001로 뜨거나, `127.0.0.1:3000`으로 접속하면 직접 호출이 403이 된다. 이전 `@CrossOrigin("*")`는 통계에 한해 이런 경우도 통과시켰다. 추측(미확인): Next rewrites가 브라우저의 `Origin` 헤더를 그대로 넘긴다면, 프록시를 거치는 POST(`/api/contents`)도 3001에서는 403이 될 수 있다. 같은 출처 POST에도 브라우저는 `Origin`을 붙이기 때문이다. → backlog
3. JWT를 쿠키로 붙이면 `allowCredentials(true)`가 필요하고, 이때 origin `*`는 쓸 수 없다(지금 구조는 이미 명시 목록이라 문제없음). `Authorization` 헤더 방식이면 지금 `allowedHeaders("*")`로 충분하다. → backlog
4. (기존 backlog 18번 줄) "하이라이트 API도 프록시 없이 직접 호출해서 같은 CORS 실패 예상, GET /api/highlights 자체가 백엔드에 없음"은 CORS 부분은 이번에, GET API는 06에서 해결된 셈이다. 다만 06도 커밋 전이고 브라우저 확인 전이라 체크하지 않고 그대로 뒀다.

---

## 정리 (트러블슈팅용)

### 1. 문제 상황
프론트(localhost:3000)가 백엔드(localhost:8080)를 직접 부르는 API가 있었다. 그런데 CORS 허용은 `StatisticsController`의 `@CrossOrigin("*")` 하나뿐이었다. 그래서 하이라이트, contents filter·check는 브라우저 preflight에서 403으로 막혔다. 프론트는 GET에도 `Content-Type: application/json`을 붙여서 모든 요청에 preflight가 나간다. 남아 있던 허용 하나도 `*`라서 아무 사이트나 호출할 수 있었다.

### 2. 고려한 선택지
- A. 백엔드 전역 CORS 설정: `CorsConfigurationSource` 빈 + 시큐리티 `.cors()`
- B. 컨트롤러마다 `@CrossOrigin`
- C. 모든 호출을 Next.js rewrites 프록시로 돌리기(같은 출처로 만들어 CORS 자체를 없앰)

### 3. 선택 이유 (트레이드오프)
A를 골랐다. 허용 규칙이 한 곳에 있고, 배포할 때는 `app.cors.allowed-origins`만 바꾸면 된다. B는 새 컨트롤러마다 붙여야 해서 빠뜨리기 쉽다. 실제로 지금 Statistics에만 붙어 있다. C는 preflight가 아예 사라지는 장점이 있다. 하지만 `next dev`/`next start` 서버가 중간에 있어야 동작하므로, 프론트를 정적 호스팅하거나 다른 서버에 올리면 배포 구조에 묶인다.
- A의 비용: 허용 목록에 없는 origin(예: 3001 포트)은 막힌다. 그래서 설정값으로 뺐다.
- MVC `addCorsMappings` 대신 시큐리티용 빈 하나만 둔 이유: 시큐리티 필터가 DispatcherServlet보다 먼저 실행된다. preflight를 필터에서 처리해야 인증 규칙이 생겨도 OPTIONS가 막히지 않는다. 설정을 두 곳에 쓰면 어긋날 수 있다.

### 4. 막혔던 부분과 해결
- **`@Nested` 테스트에서 프로퍼티를 바꿨는데 반영이 안 됐다.** `@TestPropertySource`로 origin 2개를 지정했는데 403이 나왔다. 컨텍스트에서 프로퍼티와 `CorsConfiguration` 값을 출력해 보니 둘 다 정상이었다. 원인은 `mockMvc` 필드였다. 안쪽 클래스가 바깥 클래스의 `mockMvc`를 쓰고 있었다. 이 필드는 바깥 클래스의 기본 컨텍스트(3000만 허용)에서 주입된 것이다. `@Nested` 클래스 안에 `@Autowired MockMvc`를 따로 선언해서 해결했다. 디버그 코드는 지웠다.
- **"설정 빈을 만들면 적용된다"는 가정 확인.** `.cors()`를 빼고 테스트를 돌려 보니 빈이 있어도 9개가 실패했다. 빈은 설정을 담기만 하고, 필터 체인에 연결하는 것은 `.cors()`다.
- `./gradlew clean`이 `build/` 폴더 잠금으로 한 번 실패했다(OneDrive 경로). `test --rerun-tasks`로 다시 전체 실행했다.

### 개념 메모
- **CORS와 preflight**: 브라우저는 다른 출처로 "단순하지 않은" 요청(PUT/DELETE, `Content-Type: application/json` 등)을 보내기 전에 `OPTIONS`로 먼저 허락을 묻는다. 서버가 `Access-Control-Allow-Origin` 등으로 답해야 본 요청을 보낸다. 서버 간 요청(Next 프록시 → 8080, curl)에는 브라우저가 없으니 이 검사도 없다.
- **왜 시큐리티에서도 켜야 하나**: 요청은 시큐리티 필터 체인을 먼저 지난 뒤 DispatcherServlet(MVC)에 도착한다. 인증이 필요한 경로라면 쿠키·토큰이 없는 preflight가 MVC의 CORS 처리까지 가기 전에 필터에서 401/403으로 끝난다. `.cors()`를 켜면 `CorsFilter`가 필터 체인 앞쪽에서 preflight에 바로 답한다.

---

## 복습 질문
<details>
<summary>1. <code>SecurityConfig</code>에 <code>corsConfigurationSource()</code> 빈은 그대로 두고 <code>.cors(Customizer.withDefaults())</code> 한 줄만 지우면, 허용 origin(<code>http://localhost:3000</code>)의 <code>OPTIONS /api/highlights</code> preflight는 어떤 결과가 되는가? 빈이 있는데도 그렇게 되는 이유는?</summary>

답:

</details>

<details>
<summary>2. 만약 <code>StatisticsController</code>의 <code>@CrossOrigin(origins = "*")</code>를 지우지 않고 남겨 뒀다면, <code>Origin: http://evil.example.com</code>의 <code>/api/statistics</code> preflight는 200일까 403일까? 필터 체인과 DispatcherServlet의 실행 순서로 설명해 보자. (그래서 왜 지워야 했는지도)</summary>

답:

</details>

<details>
<summary>3. (기존 코드 연결, 프론트 포함이라 답 제공) <code>getHighlights</code>는 GET인데도 preflight가 생긴다. <code>frontend/lib/api.ts</code>의 어느 코드 때문인가? 백엔드에서 <code>setAllowedHeaders(List.of("*"))</code>를 <code>List.of("Authorization")</code>으로 좁히면 무슨 일이 생기는가?</summary>

답: `request()`가 모든 요청에 `headers: { 'Content-Type': 'application/json' }`를 붙인다(`lib/api.ts:32`). `application/json`은 "단순 요청"에 허용되는 Content-Type(`text/plain`, `application/x-www-form-urlencoded`, `multipart/form-data`)이 아니다. 그래서 GET이라도 브라우저가 `Access-Control-Request-Headers: content-type`을 담아 preflight를 먼저 보낸다. 허용 헤더를 `Authorization`만으로 좁히면 `content-type`이 허용 목록에 없다. 그러면 `CorsFilter`가 preflight를 403으로 거절하고, 직접 호출하는 API 전부가 다시 막힌다. 테스트의 `preflight()`가 `Access-Control-Request-Headers: content-type`을 넣는 이유가 이것이다.

</details>
