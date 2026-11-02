# 21. 프론트 로그인 (로그인/회원가입 화면, 토큰 저장, Authorization 자동 첨부, 401→refresh→재시도, 로그아웃, 라우팅 가드)

- 브랜치: `feat/auth-member` (시작 시 `git status` 깨끗함)
- 번호: 디렉터리에 `00`~`14`, `16`~`20`이 있어 이전 task는 20이 맞고 다음 번호 21 사용
- 커밋하지 않았다. 백엔드 코드는 수정하지 않았다.
- 시작 시 작업 디렉터리가 `class-schedule-generator`(다른 프로젝트)였다. 요청 내용과 `.claude/`는 `book-log-main`에만 있어 이 저장소에서 작업했다. (task 19, 20과 같은 상황)

## 사전 조사 결과 (코드로 확인한 것)

### 백엔드 인증 계약 (`AuthController`, DTO, `SecurityConfig`)
| 엔드포인트 | 요청 | 성공 응답 | 실패 |
|---|---|---|---|
| `POST /api/auth/register` | `{username(4~20), password(8~64, UTF-8 72바이트 이하), email(형식, 100자 이하)}` | 201 `{id, username}` | 400: `@Valid` 실패는 `{필드명: 메시지}` 맵, 409: 아이디 중복 `{status, message}` |
| `POST /api/auth/login` | `{username(≤20), password(≤64)}` | 200 `{id, username, accessToken, refreshToken}` | 401 `{status, message}` (아이디 없음/비밀번호 틀림 구분 없음), 400 검증 실패 |
| `POST /api/auth/refresh` | `{refreshToken}` | 200 `{accessToken}` (**access만**, refresh 회전 없음) | 401 (위조/만료/access 토큰/회원 없음 구분 없음) |
| 로그아웃 | **없음** | - | - |

- **refresh 토큰은 쿠키가 아니라 응답 body로 온다.** `Set-Cookie`가 없고, refresh 요청도 body의 `refreshToken`만 읽는다. → 확정한 설계 방향에 따라 localStorage 저장. httpOnly 쿠키로 바꾸려면 백엔드 수정이 필요하므로 구현하지 않고 backlog에 "향후 개선안"으로만 기록했다.
- 만료: access 15분, refresh 7일 (`application-api-key.yml`, 값만 확인).
- `/api/auth/**`, Swagger, `/actuator/health`만 `permitAll`, 나머지는 `authenticated()`. 토큰이 없거나 틀리면 `JsonAuthenticationEntryPoint`가 401 `{status, message}` JSON.
- JWT claim: `sub`=userId, `username`, `role`, `type`(access/refresh). 필터는 `Authorization: Bearer`의 access만 인정(refresh를 Bearer로 보내면 401).
- CORS: `/api/**`에 허용 헤더 `*`(echo)라 `Authorization` 헤더 + preflight가 통과한다(task 19에서 테스트로 확인).

### 프론트
- Next.js 15 **app router**, React 19, Tailwind 3, lucide-react. 테스트 도구 없음(`package.json`에 jest/vitest 없음, scripts는 dev/build/start뿐).
- `lib/api.ts`: 단일 `request<T>()`와 `ApiError`, `toErrorMessages()`. 호출 경로가 둘로 갈린다: `BASE_URL`(백엔드 직접, CORS 필요: 통계/하이라이트/필터)과 `PROXY_BASE_URL=''`(Next rewrites 프록시: 검색/저장/캘린더). 인증 엔드포인트는 프록시 쪽으로 보낸다.
- `app/layout.tsx`가 모든 경로에 `Sidebar` + `Header`를 항상 그렸다 → 로그인 화면에는 사이드바가 없어야 해서 셸을 경로/로그인 상태별로 바꿔야 했다.

## 변경한 파일
| 파일 | 내용 |
|---|---|
| `frontend/lib/authStore.ts` (신규) | 인증 상태 단일 저장소. access는 모듈 변수, refresh는 localStorage, `subscribe/getSnapshot`, `setSession/setAccessToken/clearSession`, generation 카운터 |
| `frontend/lib/api.ts` | `Authorization: Bearer` 자동 첨부, 401→refresh(single-flight)→원 요청 1회 재시도, `login`/`register` 추가. 기존 API 함수 시그니처는 그대로 |
| `frontend/lib/authValidation.ts` (신규) | 백엔드 DTO 제약과 맞춘 입력 검증(72바이트 포함) |
| `frontend/components/auth/AuthProvider.tsx` (신규) | store 구독 → context(`status/username/login/logout`), 앱 시작 시 refresh로 복구, 라우팅 가드 |
| `frontend/components/auth/AuthCard.tsx` (신규) | 로그인/가입 공통 카드, `TextField`, `SubmitButton` |
| `frontend/components/layout/AppShell.tsx` (신규) | 로딩/공개 경로/보호 경로별 화면 선택 (보호 화면이 순간적으로 비치지 않게) |
| `frontend/app/layout.tsx` | Sidebar/Header 직접 렌더링 → `AuthProvider` + `AppShell`로 교체 |
| `frontend/app/login/page.tsx`, `frontend/app/signup/page.tsx` (신규) | 화면 (입력 검증, 서버 에러 표시, 제출 중 비활성화) |
| `frontend/components/sidebar/Sidebar.tsx` | 하단에 사용자명 + 로그아웃 버튼 |
| `frontend/types/index.ts` | 인증 DTO 타입 4개 추가 |
| `.claude/decisions.md`, `.claude/backlog.md` | 항목 추가만 |

변경하지 않음: 백엔드 전체, 기존 `request` 호출부(훅/컴포넌트), `next.config.js`, 의존성(`package.json`).

## 구현 요약
- **라우팅**: `/login`, `/signup`은 공개. 비로그인 + 보호 경로 → `router.replace('/login')`, 로그인 + 공개 경로 → `router.replace('/')`. 앱 시작 직후(`loading`)에는 "불러오는 중..."만 보여주고 보호 페이지/API 호출은 인증 확인 뒤에야 시작된다.
- **앱 시작**: refresh 토큰이 있으면 `refreshAccessToken()`으로 access 복구(성공 → authenticated, 실패 → unauthenticated), 없으면 바로 unauthenticated. StrictMode에서 effect가 두 번 돌아도 single-flight라 호출은 한 번.
- **가입**: 성공하면 같은 정보로 자동 로그인 → 메인. 자동 로그인이 실패하면 `/login`으로.
- **로그아웃**: 토큰만 지운다(서버 엔드포인트 없음).
- 로그인 사용자명 표시: refresh 응답에는 username이 없어서, 로그인 직후엔 응답값을, 새로고침 후엔 access 토큰 payload의 `username` claim을 읽는다(표시용, 서명 검증은 서버 몫).

## 결정 사항
`decisions.md` 참고(토큰 저장 트레이드오프, 401 처리, 인증 상태 단일 저장소, 로그아웃). 요약:
1. access 메모리 / refresh localStorage (백엔드 계약상 쿠키 불가). 남은 위험: XSS 시 refresh 탈취.
2. `/api/auth/*`는 토큰을 붙이지 않고 401에 재시도하지 않는다 → 로그인 실패 때 refresh가 호출되거나 refresh 401이 무한 루프가 되는 일이 없다.
3. 요청이 쓴 토큰이 현재 토큰과 다르면(그 사이 다른 요청이 갱신) refresh 없이 새 토큰으로 재시도만 한다.
4. 로그아웃 중 진행되던 refresh가 끝나도 세션을 되살리지 않는다(generation).

## 발견한 문제 (고치지 않고 기록만)
1. **백엔드에 로그아웃 엔드포인트가 없고 refresh를 서버가 저장하지 않는다.** 클라이언트 로그아웃 후에도 이미 발급된 refresh 토큰은 7일간 유효하다(탈취 시 폐기 불가). task 14 backlog와 같은 뿌리.
2. **refresh 토큰이 localStorage에 있어 XSS에 취약하다.** 이번 계약에서는 불가피. 완화/개선안은 backlog 추가분(httpOnly 쿠키 전환 + 백엔드 변경).
3. **refresh는 회전하지 않아서** 로그인 후 7일이 지나면 사용 중이어도 로그아웃된다(슬라이딩 없음).
4. **네트워크 오류/5xx도 refresh 실패로 보고 로그아웃시킨다.** 요구사항("실패하면 토큰 제거")을 그대로 따른 것이라 서버 재시작 직후 새로고침하면 로그인이 풀릴 수 있다. 4xx(거절)만 로그아웃시키는 쪽이 더 친절하다.
5. **보호 경로 가드가 클라이언트에서만 동작한다.** 서버(middleware)는 access 토큰이 메모리에 있어 로그인 여부를 모른다. 비로그인 사용자도 정적 JS 번들은 받을 수 있고(데이터는 API가 401로 막음), 새로고침 시 잠깐 로딩 화면이 보인다.
6. 백엔드 직접 호출(`BASE_URL`, 통계/하이라이트/필터)은 `Authorization` 헤더 때문에 **preflight가 추가로 나간다**(허용은 됨). 모든 호출을 프록시로 통일하면 줄어든다(기존 `api.ts` 주석의 "@CrossOrigin은 StatisticsController에만 있음"도 task 19 이후 사실과 다르다).
7. `GET /api/contents/check`가 `externalId`를 URL에 인코딩 없이 붙인다(기존 코드, 이번 범위 밖).
8. 계정 로그인 실패 메시지는 백엔드 문구를 그대로 보여준다. 문구 자체는 구분 없는 한 가지라 열거 위험은 없다.
9. **`./gradlew test`를 그대로 실행하면 `HighlightRepositoryTest`가 실제 DB(application.yml)에 붙는다**(task 20 발견 1번이 아직 미해결). 이번에도 요청대로 그대로 실행했다.
10. Docker 29 + Testcontainers 1.20.4 비호환 때문에 `ContentRepositoryPostgresTest` 9개가 skip된다(task 20 발견 2번이 아직 미해결). `BUILD SUCCESSFUL`이어도 skip 수를 봐야 한다.

## 테스트 / 검증 결과
### 프론트에는 테스트 환경이 없어 도구를 도입하지 않았다
임시 검증(저장소 밖 scratchpad의 일회용 스크립트, 저장소에 추가하지 않음): `api.ts`/`authStore.ts`를 TypeScript로 트랜스파일해 가짜 `fetch`/`localStorage`로 실행. **9개 시나리오 모두 통과(0 실패).**
- Authorization 헤더 첨부 / 로그인 요청은 헤더 없음 + 401이어도 refresh 안 부름
- 401 → refresh → 원 요청 재시도 성공
- refresh 실패(401) → 토큰 제거(access, localStorage), unauthenticated, refresh 재호출 없음
- 동시 3요청 401 → refresh **1회**
- 재시도도 401 → 추가 재시도 없음(요청 2회 + refresh 1회)
- refresh 도중 로그아웃 → 세션이 되살아나지 않음
- refresh 토큰 없음 → 호출 없이 false
- 늦게 도착한 401 → 이미 갱신된 토큰으로 재시도만(refresh 1회)

정적 검증: `npx tsc --noEmit` 오류 0, `npx next build` 성공(`/login`, `/signup` 포함 9개 페이지).

### `./gradlew test` (백엔드, 변경 없음)
`BUILD SUCCESSFUL`, **214개 실행, 실패 0, 에러 0, skip 9** (skip 9 전부 `ContentRepositoryPostgresTest` — 위 발견 10번).

### 수동 검증 시나리오 체크리스트
**아래는 모두 아직 브라우저에서 실행하지 않았다.** (백엔드 기동에 실제 DB/비밀 설정이 필요해 이번에는 띄우지 않았다.) 실행 후 결과를 채울 것. 준비: 백엔드 `localhost:8080`, `cd frontend && npm run dev`, 개발자도구 Network/Application 탭.

- [x] 1. 비로그인 상태에서 `/` 접근 → `/login`으로 이동, 사이드바/헤더 없음, 보호 API 호출이 Network에 없음
  - 확인 완료: /login 화면 표시, 사이드바/헤더 없음, 로그인 화면에서 보호 API 호출 없음
- [x] 2. `/signup` 빈 값/짧은 아이디(3자)/짧은 비밀번호(7자)/이메일 형식 오류 → 필드별 메시지, 요청 안 나감
- [x] 3. 한글 25자 비밀번호 → 72바이트 메시지, 요청 안 나감
- [x] 4. 정상 가입 → 자동 로그인되어 `/` 이동, 사이드바에 사용자명 표시
- [x] 5. 같은 아이디로 재가입 → 409 메시지 표시, 버튼 다시 활성화
- [x] 6. 제출 중 버튼이 비활성화+"가입 중..."(Network를 Slow 3G로 두고 확인), 더블 클릭해도 요청 1건
- [x] 7. 로그아웃 → `/login`, Application > Local Storage의 `archivio.refreshToken` 삭제됨
- [x] 8. 틀린 비밀번호 / 없는 아이디 로그인 → 같은 메시지, 이때 `/api/auth/refresh` 호출 없음
- [x] 9. 정상 로그인 → `/`, 요청 헤더에 `Authorization: Bearer ...`, access 토큰은 localStorage에 **없고** refresh만 있음
- [x] 10. 로그인 상태에서 `/login`, `/signup` 직접 입력 → `/`로 이동
- [x] 11. 로그인 상태에서 새로고침 → 로딩 후 `/refresh` 1회 호출, 로그인 유지 + 사용자명 표시(JWT claim)
- [x] 12. 캘린더/하이라이트 페이지가 데이터를 정상 로드(사이드바 미니 캘린더와 본문이 동시에 호출)
- [x] 13. access 만료 재현: 백엔드 `jwt.access-token-expiration`을 짧게(예: 10초) 하고 재기동 후 로그인 → 10초 뒤 데이터 요청 → 401 → `/refresh` → 같은 요청 재시도 성공, 화면 정상
- [x] 14. 위 상태에서 미니 캘린더+본문이 동시에 401을 받아도 `/refresh`는 1회만
- [x] 15. refresh 토큰 만료/위조 재현: localStorage의 refresh 값을 지우거나 망가뜨리고 데이터 요청 발생(또는 새로고침) → 토큰 제거 + `/login` 이동
- [x] 16. 백엔드를 끄고 새로고침 → 로그인 화면으로 간다(발견 4번의 현재 동작 확인)

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: 백엔드가 JWT 인증을 강제(task 19)하자 프론트의 모든 API가 401이 됐다. 로그인 화면, 토큰 보관, 만료된 access의 자동 재발급, 동시 요청 처리, 비로그인 접근 차단이 필요했다.
2. **고려한 선택지**: 토큰 보관 — ① 둘 다 localStorage ② access 메모리 + refresh localStorage ③ httpOnly 쿠키(백엔드 수정 필요). 재발급 — ⓐ 요청마다 독립적으로 refresh ⓑ single-flight(진행 중 Promise 공유).
3. **선택 이유(트레이드오프)**: ②와 ⓑ. 백엔드가 refresh를 body로만 줘서 ③은 지금 불가능하고, ②는 access 노출 면적을 줄인다(비용: 새로고침마다 refresh 1회, 남는 위험: localStorage의 refresh XSS). ⓐ는 동시에 N개가 401을 받으면 refresh가 N번 나가고 순서가 꼬일 수 있다.
4. **막혔던 부분과 해결**: ① 로그인 자체가 401을 주는데 "401이면 refresh" 규칙을 그대로 쓰면 로그인 실패가 refresh를 호출하고 refresh 401이 무한 루프가 될 수 있어서 `/api/auth/*`는 제외했다. ② 3개 요청이 동시에 401이어도 refresh가 한 번만 나가도록 했는데, 한 요청의 401 응답이 refresh 완료 뒤에 늦게 도착하면 또 refresh를 부르는 틈이 있어서 "요청에 쓴 토큰과 현재 토큰이 다르면 재시도만"으로 막았다. ③ 로그아웃 직후 늦게 끝난 refresh가 세션을 되살리는 문제는 generation 카운터로 막았다. ④ `api.ts`(React 밖)와 화면이 같은 상태를 봐야 해서 context만으로는 안 되고 구독 가능한 store를 단일 출처로 두었다.

## 복습 질문

<details>
<summary>Q1. (기존 백엔드 연결) 프론트가 보내는 <code>Authorization: Bearer</code> 헤더는 백엔드에서 어떤 순서로 처리돼 컨트롤러의 로그인 사용자 정보가 되나요? 그리고 refresh 토큰을 Bearer로 보내면 어떻게 되나요?</summary>

**답**: 요청이 시큐리티 필터 체인에 들어오면 `.cors()`가 먼저 preflight를 처리하고, `JwtAuthenticationFilter`(`UsernamePasswordAuthenticationFilter` 앞)가 `Authorization` 헤더에서 `Bearer ` 뒤 토큰을 꺼내 `JwtTokenProvider.parse(token, ACCESS)`로 검증한다. 통과하면 토큰의 claim(sub=userId, username, role)만으로 `AuthenticatedMember`를 만들어 `SecurityContext`에 넣는다(DB 조회 없음, task 14 결정). 그 뒤 `authorizeHttpRequests`의 `authenticated()` 규칙이 이를 확인하고, 컨트롤러는 `@AuthenticationPrincipal AuthenticatedMember`로 받아 `member.id()`를 서비스에 넘긴다(task 19). 인증이 없으면 `JsonAuthenticationEntryPoint`가 401 JSON을 쓴다. refresh 토큰을 Bearer로 보내면 type claim이 `refresh`라 `parse(..., ACCESS)`가 빈 값을 돌려주고 인증 없이 통과 → 401이다. 그래서 프론트는 refresh 토큰을 `/api/auth/refresh`의 body로만 보낸다.
</details>

<details>
<summary>Q2. (프론트 로직) 화면 세 곳이 동시에 데이터를 요청했는데 access 토큰이 만료돼 모두 401을 받았다면, <code>/api/auth/refresh</code>는 몇 번 호출되고 각 요청은 어떻게 이어지나요? 왜 <code>/api/auth/*</code>는 이 규칙에서 빼야 하나요?</summary>

**답**: refresh는 1번이다. 첫 401이 `refreshAccessToken()`으로 Promise를 만들어 `refreshInFlight`에 두고, 나머지 두 요청은 같은 Promise를 기다린다(single-flight). 끝나면 세 요청 모두 새 access 토큰으로 원래 요청을 1회씩 재시도한다. 재시도가 또 401이면 더 재시도하지 않고 실패한다. `/api/auth/*`를 빼는 이유: 로그인 401은 "비밀번호 틀림"이라 refresh로 해결되지 않고, refresh 자신의 401은 "refresh 만료"라 다시 refresh를 부르면 무한 루프가 되기 때문이다. 또 refresh는 `request()`를 거치지 않고 fetch를 직접 쓴다.
</details>

<details>
<summary>Q3. (프론트 개념, 풀이 제공) access 토큰을 localStorage가 아니라 메모리에 두면 무엇이 좋아지고, 무엇이 여전히 위험하고, 어떤 비용이 생기나요?</summary>

**답**: 좋아지는 점: XSS 스크립트가 `localStorage.getItem`으로 토큰 값을 통째로 읽어 외부로 빼가기 어려워진다(탈취 면적 축소). 여전히 위험한 점: 같은 페이지에서 실행되는 악성 스크립트는 앱의 `api.ts`를 그대로 호출해 요청을 보낼 수 있고, 이번 구조에서는 refresh 토큰이 localStorage에 있어 XSS가 그것을 읽으면 사이트 밖에서도 access를 계속 발급받는다. 비용: 새로고침/새 탭마다 메모리가 비어 refresh를 한 번 호출해 복구해야 하고(로딩 상태 필요), 앱 시작 순서와 동시 요청(single-flight)을 직접 관리해야 한다. httpOnly 쿠키는 JS가 못 읽는 점이 더 안전하지만 CSRF 대응과 백엔드 변경이 필요해 backlog로 남겼다.
</details>

## 수동 검증 중 수정

### 증상
`localhost:3000/login` Console에 반복 출력: `The result of getServerSnapshot should be cached to avoid an infinite loop` (위치 `AuthProvider.tsx:39`, `useSyncExternalStore` 호출부). 수동 검증 1번의 "Console 에러 5개"가 이것이다.

### 원인 (코드로 확인)
- `authStore.ts`의 `getSnapshot`은 모듈 변수 `snapshot`을 그대로 반환하고, `emit()`에서만 교체하므로 참조가 안정적이었다. **문제 없음.**
- `getServerSnapshot`이 `() => ({ status: 'loading', username: null })`로 **호출마다 새 객체**를 만들어 반환했다. React는 같은 렌더에서 이 함수를 두 번 불러 `Object.is`로 비교하는데 매번 다르니 "캐시되지 않았다"며 경고한다. (서버 렌더링과 클라이언트 하이드레이션에서 이 함수가 쓰이므로 `/login` 첫 로드마다 발생.)
- 부수 관찰: `setAccessToken`(refresh 성공)은 값이 같아도 매번 새 스냅샷을 만들어 불필요한 리렌더를 일으킬 수 있었다. 무한 루프 원인은 아니지만 "상태가 바뀔 때만 새 객체" 원칙에 맞춰 같이 정리했다.

### 수정 (`frontend/lib/authStore.ts`만)
1. 모듈 레벨 고정 객체 `SERVER_SNAPSHOT` 추가, `getServerSnapshot`은 항상 이것을 반환. 초기 `snapshot`도 같은 객체로 시작(서버/클라이언트 첫 값이 참조까지 일치).
2. `emit()`에서 `status`와 `username`이 현재 스냅샷과 같으면 아무것도 하지 않음(참조 유지, 리스너 미호출). 다를 때만 새 객체로 교체.
- `AuthProvider.tsx`, 인증 로직, 백엔드, 의존성은 변경하지 않았다. 커밋하지 않았다.

### 검증
- `npx tsc --noEmit`: 오류 0
- `npx next build`: 성공 (9개 페이지, `/login`·`/signup` 포함)

### 수동 확인 방법 (아직 브라우저에서 실행하지 않음)
1. `cd frontend && npm run dev` 재시작 후 브라우저 하드 리로드(Ctrl+Shift+R). 기존 Console은 Clear.
2. `localhost:3000/login` 접속 → Console에 위 경고가 **0건**인지 확인 (Preserve log 끄고 새로고침 몇 번).
3. `/`(비로그인) → `/login` 리다이렉트, 로그인 → `/`, 새로고침 시에도 경고가 없는지 확인.
4. 수동 검증 체크리스트 1번의 "Console 에러 5개" 항목을 다시 보고 남은 에러가 있으면 별도 원인이니 기록.

### 같은 패턴 탐색 결과 (보고만, 추가 수정 없음)
- `frontend/` 소스(app, components, lib, hooks, types)에서 `useSyncExternalStore`/`getSnapshot`/`getServerSnapshot`은 `AuthProvider.tsx`와 `authStore.ts` 두 곳뿐이다. 같은 경고를 낼 수 있는 곳은 더 없다.
- 호출마다 새 객체를 반환하는 다른 코드(`AddHighlightModal.tsx`의 `buildDto()` 등)는 이벤트 핸들러에서 쓰는 일반 함수이고 외부 스토어 스냅샷이 아니라 해당 없음.

### 발생한 문제
- 회원가입 시 이메일 중복 검사가 없음 (아이디만 다르면 같은 이메일로 가입됨), 별도 TASK로 처리