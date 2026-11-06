# 25-d. CORS 허용 origin 환경변수화 + 프론트 API 주소(NEXT_PUBLIC_API_URL) 빌드 주입

- 번호: 25-a~25-c와 같은 규칙(`25-x-이름.md`). 커밋하지 않음.
- 목표: 배포(Cloud Run + Vercel) 전에 ① 백엔드 CORS 허용 origin을 `APP_CORS_ALLOWED_ORIGINS`로 주입하고 ② 프론트의 API 주소를 `NEXT_PUBLIC_API_URL` 하나로 빌드 시점에 안전하게 주입하는 구조를 확정한다.
- 범위 밖: Swagger/actuator/헬스체크(25-e), Cloud Run 배포(25-f), 외부 API 방어(25-g), 스모크 테스트(25-h), Dockerfile, 인증/JWT 로직.

## 작업 전 현황 (코드 수정 전에 확인한 사실)

### 백엔드 CORS (`config/SecurityConfig.java`)
| 항목 | 현재 |
|---|---|
| origin | 하드코딩 아님. `@Value("${app.cors.allowed-origins:http://localhost:3000}") String[]`. 쉼표 분리는 Spring 변환기에 맡기고 **공백·끝 슬래시 정리 없음**, `*` 검사 없음 |
| allowCredentials | **설정 안 함(=false)** |
| allowedMethods | GET, POST, PUT, DELETE, OPTIONS (PATCH/HEAD 없음, 현재 쓰는 API엔 불필요) |
| allowedHeaders | `*` (credentials가 없으므로 `*` 사용 가능. 요청 헤더를 그대로 echo해서 Authorization도 응답에 명시됨 — decisions 2026-11-01) |
| 적용 경로 | `/api/**`만 (`/actuator/**`, Swagger는 CORS 대상 아님) |
| preflight | `.cors()`가 등록한 CorsFilter가 인가 필터보다 먼저 응답 → permitAll 불필요 |
| 기존 테스트 | `CorsConfigTest`가 허용/비허용 origin, preflight(Authorization), 401에도 헤더가 붙는지, `@TestPropertySource`로 다중 origin 검증 |

### 프로퍼티/환경변수/문서
- 프로퍼티 이름: `app.cors.allowed-origins` (kebab-case, 기존 `api.kakao-key`, `jwt.access-token-expiration`과 같은 규칙). 환경변수 `APP_CORS_ALLOWED_ORIGINS`로 바인딩됨(`EnvironmentBindingTest.corsValue`가 이미 확인).
- 루트 `.env.example`에 `APP_CORS_ALLOWED_ORIGINS=`가 **이미 있음**(25-a). 25-a 환경변수 표에도 "운영에선 사실상 필수(기본값은 localhost)"로 이미 있음. 다만 값 형식(쉼표, 끝 슬래시 금지, `*` 금지) 설명과 "비어 있으면 어떻게 되는지"는 어디에도 없다.
- 운영용 `application-prod.yml`은 없다(25-a 결정: 새 yml 없이 환경변수). Dockerfile에도 `SPRING_PROFILES_ACTIVE`는 없다 → "prod 프로파일"은 아직 존재하지 않는 개념이라 배포 시(25-f) 환경변수로 켜야 한다.

### 프론트 API 주소 (`frontend/`)
- `lib/api.ts:17` `process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'` — **localhost 폴백 하드코딩**, 끝 슬래시 처리 없음, 값이 없어도 production에서 조용히 localhost로 감.
- `next.config.js:8` 같은 폴백이 **한 번 더** 중복(rewrites의 destination).
- 그 외 `localhost`, `8080`, `process.env` 사용처는 위 두 곳뿐(grep 확인).
- 호출 경로가 두 갈래: ① 프록시(`PROXY_BASE_URL=''`, Next rewrites → 서버끼리 호출이라 CORS 무관): auth, calendar, search, contents 저장 ② 브라우저가 백엔드로 **직접**(`BASE_URL`): statistics, contents filter/check, highlights → 이쪽만 CORS 대상. 이 이중 구조는 backlog에 "통일 여부 결정 필요"로 있고 이번 요청 범위가 아니라 **유지**한다.
- `lib/api.ts:19-20` 주석이 "@CrossOrigin은 StatisticsController에만 있음"이라고 되어 있는데 이미 사실이 아님(전역 CORS로 대체됨, backlog에 25-d로 지정) → 이번에 같은 파일을 고치므로 주석만 바로잡는다.
- env 파일: `frontend/.env.local.example`(값 `http://localhost:8080` 포함)이 있고 `.gitignore`는 `.env`, `.env*.local`을 무시한다. `frontend/.env.example`은 없음.
- `package.json`에 **lint 스크립트와 eslint가 없다**(scripts: dev/build/start). 아래 "발견한 문제" 참고.

## 변경/추가 파일
| 파일 | 내용 |
|---|---|
| `config/CorsOrigins.java` | 신규. 쉼표 분리, trim, 끝 `/` 제거, 빈 값 무시, 중복 제거, `*` 포함 시 `IllegalStateException` |
| `config/SecurityConfig.java` | `@Value String[]` 제거 → `corsConfigurationSource(Environment)`에서 `CorsOrigins.parse`. 비어 있으면 prod 프로파일은 기동 실패, 아니면 `http://localhost:3000`. allowCredentials/allowedHeaders/Methods는 그대로 |
| `test/.../CorsOriginsTest.java` | 신규 8개 |
| `test/.../CorsConfigTest.java` | 테스트 2개 추가, 다중 origin 케이스를 공백/끝 슬래시/빈 항목이 섞인 값으로 변경 |
| `frontend/next.config.js` | `NEXT_PUBLIC_API_URL`을 읽는 유일한 곳. 함수형 config(phase). 아래 참고 |
| `frontend/lib/api.ts` | localhost 폴백 제거(`process.env.NEXT_PUBLIC_API_URL`만 사용), 낡은 주석 정정 |
| `frontend/.env.example` | 신규. 이름만 |
| 루트 `.env.example`, `25-a-runtime-config.md` 표의 CORS 행(+`SPRING_PROFILES_ACTIVE` 행), `decisions.md` | 반영 |

변경하지 않은 것: application*.yml, Dockerfile, 인증/JWT, 프록시/직접 호출 이중 구조, `frontend/.env.local.example`, 25-e 이후 항목.

## 결정 사항
1. **prod에서 비어 있으면 기동 실패 — 적용.** 이유: 25-a가 "필수 설정 누락은 런타임이 아니라 기동에서 드러낸다"(ApiConfig `@NotBlank`)로 정했고, 값이 빠진 채 localhost만 허용하면 서버는 정상(health 200)인데 프론트만 전부 CORS로 실패해 원인 찾기가 어렵다. 
   - 한계: "prod"는 `SPRING_PROFILES_ACTIVE=prod`일 때만 켜진다(yml·Dockerfile에 없음). 배포(25-f)에서 지정을 빠뜨리면 이 보호가 꺼지고 localhost만 허용된다 → 보안상 열리는 쪽은 아니라 안전하게 깨지지만, 25-f 체크리스트에 넣어야 한다.
   - 대안 ① 항상 기동 실패(로컬도 값 필요: 개발 불편, 로컬 yml은 gitignore라 나는 수정 불가) ② 항상 localhost 기본값(현 상태, 운영 누락 감지 불가) ③ prod 프로파일만 실패(채택).
2. **와일드카드 금지**(`*`, `https://*.vercel.app` 포함, 파싱 단계에서 기동 실패). 이유는 decisions.md 참고. Spring `allowedOriginPatterns`로 서브도메인 패턴을 허용하는 방법도 있으나 Vercel 미리보기 URL 전체가 열리므로 쓰지 않는다. 필요하면 origin을 쉼표로 추가한다.
3. **allowCredentials 불필요(false 유지).** 토큰을 localStorage(refresh) + 메모리(access)에 두고 `Authorization: Bearer`를 JS가 직접 붙이므로 브라우저가 쿠키를 자동 전송할 일이 없다. `credentials: 'include'`도 쓰지 않는다. allowCredentials=true는 cookie/HTTP 인증/클라이언트 인증서를 쓰는 요청에만 필요하다. 켜면 origin에 `*` 불가, 오설정 시 CSRF 표면 증가. 쿠키 방식 전환(backlog) 때만 재검토. 테스트로 응답에 `Access-Control-Allow-Credentials`가 없음을 고정했다.
4. **프론트 설정 위치를 next.config.js 한 곳으로.** api.ts와 rewrites가 각자 폴백을 갖고 있던 중복을 없앴다. config가 검증·정규화한 값을 `env`로 번들에 박는다. 코드에는 localhost 문자열이 `next.config.js`의 `DEV_API_URL` 한 줄만 남았고(개발 서버에서만 사용), api.ts에는 없다.
5. **NEXT_PUBLIC은 빌드 시점 고정.** `next build`가 `process.env.NEXT_PUBLIC_API_URL`을 문자열로 치환해 JS 번들에 박는다. 런타임(Cloud Run env 등)에서 바꿔도 이미 만들어진 번들은 안 바뀐다 → 빌드 단계(Vercel 프로젝트 환경변수, Docker면 `--build-arg`)에 주입해야 하며 환경별로 다시 빌드한다. rewrites의 destination도 빌드 때 `routes-manifest.json`에 고정되는 것을 확인했다. `NEXT_PUBLIC_` 접두사 변수는 브라우저에 그대로 노출되므로 시크릿 금지(현재 이 접두사 변수는 API URL 하나뿐이고 공개 주소다).
6. **프록시/직접 호출 이중 구조는 유지.** 통일은 backlog의 별도 결정(요청 범위 아님). 영향: 직접 호출(통계, 하이라이트, contents filter/check)만 백엔드 CORS가 필요하다. 프록시 호출(검색, 캘린더, 인증, 콘텐츠 저장)은 Next 서버가 백엔드를 부르므로 Origin이 없어 CORS와 무관하다.

## 배포 순서 (프론트 빌드는 백엔드 URL, 백엔드 CORS는 프론트 URL이 필요한 순환)
1. **백엔드 먼저 배포.** `APP_CORS_ALLOWED_ORIGINS`에는 임시 https 값(prod 프로파일은 빈 값이면 기동 실패하므로)을 넣는다. → Cloud Run URL 확정.
2. **프론트 빌드·배포.** 그 URL을 `NEXT_PUBLIC_API_URL`로 빌드 시점에 주입(없으면 빌드 실패). → Vercel URL 확정.
3. **백엔드의 `APP_CORS_ALLOWED_ORIGINS`를 Vercel URL로 갱신하고 재배포**(Cloud Run은 환경변수 변경이 새 리비전). 확인: 브라우저에서 직접 호출 API(통계 등)가 응답.
- 순환을 없애는 방법: 커스텀 도메인을 먼저 정해 두면 1단계부터 최종 origin을 넣을 수 있다. 프론트 도메인이 바뀌면(미리보기 URL 포함) 3단계를 다시 해야 한다.

## 401/403 응답의 CORS 헤더 (확인만)
- **붙는다.** `.cors()`가 만든 CorsFilter가 인증 필터·예외 처리보다 앞에서 응답 헤더를 먼저 써 두므로, 인증 실패(401, `JsonAuthenticationEntryPoint`)와 컨트롤러의 403(남의 하이라이트, `ForbiddenResourceException`) 모두 허용 origin이면 `Access-Control-Allow-Origin`이 붙는다. 테스트로 확인(401은 기존, 403은 이번에 추가). 안 붙으면 브라우저는 상태 코드 대신 CORS 에러로만 보여 task 21의 401 → refresh 흐름이 깨진다.
- **비허용 origin은 403이고 CORS 헤더 없음**(CorsFilter 기본 응답, 본문은 ErrorResponse 형식이 아님 — 기존 backlog).

## 검증 결과 (실행한 것만)
- `./gradlew cleanTest test`: BUILD SUCCESSFUL, **244개, 실패 0, 에러 0, skipped 9**(234 + 신규 10). skipped 9는 25-b/25-c와 같은 `ContentRepositoryPostgresTest`(Testcontainers가 Docker를 인식 못 함) — 통과가 확인되지 않은 상태 그대로다.
- 환경변수 바인딩: 기존 `EnvironmentBindingTest.corsValue`가 `APP_CORS_ALLOWED_ORIGINS` → `app.cors.allowed-origins` 이름을 확인하고, 신규 `CorsOriginsTest.envVarReachesCorsConfiguration`이 systemEnvironment 소스로 넣은 지저분한 값이 실제 `CorsConfiguration`의 허용 목록까지 이어짐을 확인(CorsConfigTest는 `@TestPropertySource`라 환경변수 경로가 아님).
- 프론트 `npm run build`: 값 없음 → **exit 1, 명확한 에러 메시지로 빌드 실패**. `NEXT_PUBLIC_API_URL=https://api.example.test/`(끝 슬래시) → 성공, 번들에는 `https://api.example.test`(슬래시 없음)만 있고 `localhost:8080`은 `.next/static`·`routes-manifest.json` 어디에도 없음. 개발 단계(`phase-development-server`)는 `http://localhost:8080`, 끝 슬래시/공백은 정리됨을 node로 호출해 확인.
- 하드코딩 grep(`localhost`, `:8080`, ts/tsx/js/json, node_modules·.next·lock 제외): `next.config.js`의 `DEV_API_URL` 한 줄뿐.
- `npx tsc --noEmit`: 통과.
- **못 한 것**: ① 프론트 **lint는 실행하지 못했다** — `package.json`에 lint 스크립트와 eslint 의존성이 없다(발견 1). 대신 `tsc --noEmit`과 `next build`(타입 검사 포함)만 통과 확인. ② `next dev`/`next start`를 실제로 띄워 브라우저로 호출해 보지는 않았다. ③ 실제 Cloud Run/Vercel 순서 검증은 25-f.

## 발견한 문제 (고치지 않음)
1. **프론트에 lint가 없다.** `next lint`를 쓰려면 eslint-config-next 설치가 필요하다(요청서의 "lint 통과" 확인 불가). 추가할지 별도 task로 결정 필요.
2. `frontend/.env.local.example`과 새 `frontend/.env.example`이 둘 다 있어 역할이 겹친다(전자는 값이 들어 있음). 하나로 합칠지 결정 필요.
3. `frontend/tsconfig.tsbuildinfo`가 git에 추적되는 빌드 산출물이라 빌드/`tsc` 때마다 변경된다(이번엔 되돌려 두었다).
4. 허용 origin 목록에 `http://localhost:3001`/`127.0.0.1:3000`이 없어 개발 서버 포트가 바뀌면 직접 호출이 403(backlog 기존 항목). 환경변수로 추가 가능해졌으므로 해결 수단은 생겼으나, 로컬 yml/환경변수는 건드리지 않았다.
5. origin 문자열 형식(스킴 포함 여부, 경로가 붙은 값)은 검증하지 않는다. `book-log.example.com`처럼 스킴이 빠진 값은 조용히 아무 요청도 매치하지 않는다.
6. 비허용 origin의 403 본문이 ErrorResponse 형식이 아니다(기존 backlog, 미검증 상태 유지).
7. `SPRING_PROFILES_ACTIVE`가 Dockerfile/배포 설정 어디에도 없다 → 25-f에서 `prod`를 지정하지 않으면 결정 1의 기동 실패 보호가 동작하지 않는다.

## 남은 TODO
- 25-f: `SPRING_PROFILES_ACTIVE=prod`, `APP_CORS_ALLOWED_ORIGINS`, 프론트 빌드 시 `NEXT_PUBLIC_API_URL` 주입, 위 배포 순서 수행.
- 프론트 lint 도입 여부(발견 1), `.env.local.example` 정리(발견 2).
- backlog: 프록시/직접 호출 통일 결정(`lib/api.ts`), 비허용 origin 403 본문 형식.

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: 허용 origin이 기본값(localhost)에 의존하고 값 정리가 없어 배포 주소를 넣을 때 끝 `/`나 공백 하나로 CORS가 실패할 수 있었다. 프론트는 API 주소가 api.ts와 next.config.js에 localhost 폴백으로 중복되어, 환경변수를 빠뜨려도 production이 조용히 localhost를 가리켰다.
2. **고려한 선택지**: 백엔드 ① 코드/yml 하드코딩 ② 환경변수(채택) ③ 와일드카드·패턴. 프론트 ① 런타임 설정 API 조회 ② 빌드 시점 `NEXT_PUBLIC_*` 주입(채택) ③ 전부 프록시로 통일해 CORS 자체를 제거.
3. **선택 이유**: 환경변수는 이미지 재빌드 없이 origin만 바꿀 수 있고 25-a 방침과 같다. `*`는 명시 origin 규칙을 단순하게 유지하려고 금지. 프론트는 구현이 가장 단순한 빌드타임 주입을 택하되 production에서 값이 없으면 빌드가 실패하게 해 "조용한 localhost"를 막았다. 트레이드오프: 환경마다 재빌드해야 하고 프론트 URL이 바뀌면 백엔드 CORS 값도 같이 갱신해야 한다(배포 순환).
4. **막혔던 부분과 해결**: next.config.js와 api.ts 두 곳의 폴백을 어떻게 하나로 모을지가 문제였다. config(CommonJS)가 값을 검증하고 `env`로 번들에 박으면 api.ts는 읽기만 하면 된다. "prod 프로파일이 없다"는 사실은 코드 확인으로 알았고, 실패 보호가 `SPRING_PROFILES_ACTIVE=prod`에 의존하는 한계를 결정 1에 명시했다.

## 복습 질문
<details><summary>Q1. 허용 origin을 코드/설정 파일이 아니라 환경변수(APP_CORS_ALLOWED_ORIGINS)로 뺀 이유는? 끝 슬래시를 제거하고 `*`를 금지한 이유도 설명하라.</summary>

(여기에 답)

</details>

<details><summary>Q2. NEXT_PUBLIC_API_URL은 왜 런타임이 아니라 빌드 시점에 값이 정해지나? Cloud Run에서 환경변수만 바꿔 재시작하면 왜 프론트 주소가 안 바뀌나? (프론트 질문이라 답을 같이 준다)</summary>

`next build`가 소스의 `process.env.NEXT_PUBLIC_API_URL`을 그 순간의 값으로 **문자열 치환**해 브라우저용 JS 번들에 박는다. 브라우저에는 `process.env`가 없으므로 실행 시점에 읽을 방법이 없고, 서버 환경변수를 바꿔도 이미 만들어진 번들은 그대로다. 그래서 값을 바꾸려면 다시 빌드해야 하고, 이 값은 번들에 들어가 누구나 볼 수 있으므로 시크릿이면 안 된다.

</details>

<details><summary>Q3. (기존 코드 연결, task 19) 401/403은 GlobalExceptionHandler가 아니라 JsonAuthenticationEntryPoint/JsonAccessDeniedHandler가 쓴다. 그런데 이 응답에도 CORS 헤더가 붙는 이유는? 안 붙으면 프론트의 401 → refresh 재시도(task 21)에 어떤 일이 생기나?</summary>

(여기에 답)

</details>
