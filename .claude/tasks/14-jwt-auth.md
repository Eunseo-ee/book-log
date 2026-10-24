# 14. JWT 발급/검증 + 인증 필터 + refresh

- 브랜치: `feat/auth-member` (시작 시 `git status` 깨끗함, 원격은 Eunseo-ee/book-log 확인)
- 참고: `.claude/decisions.md` 2026-10-24 항목들, `.claude/tasks/13-member-auth.md`, `.claude/tasks/12-security-merge-investigation.md` (발견 사항 3·6)
- `origin/feat/security`는 읽지도 머지하지도 않았다. 로컬 `application.yml`, `application-api-key.yml`은 수정·이동·삭제하지 않았다. 서버(bootRun)는 실행하지 않았다.
- **보고할 실수:** 작업 초반 설정 파일 구조를 보려고 `application.yml`을 `sed`로 마스킹해 출력했는데, 마스킹 패턴이 datasource 접속 URL/사용자명 줄을 가리지 못해 그 줄들이 내 작업 화면에 한 번 출력됐다(추적 파일·로그·이 문서에는 쓰지 않았다). 비밀번호/API 키/JWT 시크릿 줄은 출력되지 않았다. 이후에는 키 이름만 뽑는 방식으로 확인했다. 마음에 걸리면 Supabase 접속 정보 교체를 검토.

## 배경
task 13에서 Member와 가입/로그인(토큰 없이)을 만들었다. 이번 task는 JWT 발급과 인증 필터까지다. 접근 제어는 아직 `anyRequest().permitAll()` 그대로(authenticated 전환은 task 19).

## 범위
- 수정 허용: `build.gradle`, `config/JwtProperties`·`security/*`·`validation/*`·DTO·예외(신규), `config/SecurityConfig`, `service/AuthService`, `controller/AuthController`, `dto/request/RegisterRequestDto`, `exception/GlobalExceptionHandler`(핸들러 1개 추가만), 테스트, `.claude/decisions.md`·`backlog.md`(추가만), 이 파일
- 범위 밖(손대지 않음): Content↔Member 연결, userId 변경, CalendarController의 `userId = 1L`, authenticated 전환, AuthenticationEntryPoint/AccessDeniedHandler, 프론트, 로그아웃/블랙리스트, 로그인 시도 제한, 이메일 중복, DB 마이그레이션

## 설계
| 항목 | 내용 |
|---|---|
| 의존성 | jjwt **0.12.6** (`jjwt-api` implementation, `jjwt-impl`·`jjwt-jackson` runtimeOnly). 0.12.x 라인의 `Jwts.parser().verifyWith(key)` / `parseSignedClaims` API 사용 |
| 설정 읽기 | `config/JwtProperties` — 기존 `ApiConfig` 패턴(`@Configuration` + `@ConfigurationProperties(prefix = "jwt")` + Lombok getter/setter). 키: `jwt.secret`(BASE64), `jwt.access-token-expiration`(ms), `jwt.refresh-token-expiration`(ms). **기본값 없음.** `InitializingBean.afterPropertiesSet`에서 검증: 시크릿 없음/BASE64 아님/디코딩 32바이트 미만, 만료 시간 없음/0 이하 → `IllegalStateException`으로 앱 시작 실패. 메시지에는 키 이름만 있고 값은 없다(BASE64 디코딩 예외는 메시지에 값이 섞일 수 있어 원인으로 연결하지 않음) |
| 토큰 | `security/JwtTokenProvider`. subject=`Member.id`, claim: `username`, `role`, `type`(`access`/`refresh`). HS256(키 길이에 따라 jjwt가 선택). `parse(token, expectedType)`은 서명·만료·형식·type·claim 값(subject 숫자, role enum)을 확인하고 실패하면 사유만 WARN 로그(토큰 값 없음) 후 `Optional.empty()` |
| principal | `security/AuthenticatedMember(Long id, String username)` record. 권한은 role claim으로 `SimpleGrantedAuthority` |
| 필터 | `security/JwtAuthenticationFilter` (OncePerRequestFilter). `Authorization: Bearer`(접두어 대소문자 무시)의 **access 토큰만** 인증. 어떤 경우에도 예외 없이 다음 필터로 진행. DB 조회 없음 |
| SecurityConfig | CORS·CSRF 비활성화·`permitAll`·PasswordEncoder 유지 + 세션 STATELESS + `addFilterBefore(new JwtAuthenticationFilter(...), UsernamePasswordAuthenticationFilter.class)`. `filterChain(HttpSecurity, JwtTokenProvider)`로 시그니처 변경 |
| 로그인 응답 | `LoginResponseDto(id, username, accessToken, refreshToken)`. `@Getter`만(toString 없음). password/해시 필드 자체가 없음 |
| refresh | `POST /api/auth/refresh` body `{refreshToken}` → `{accessToken}`. `AuthService.refresh`: refresh 타입만 인정 → 회원 DB 확인(`findById`) → 새 access만 발급(refresh 회전 없음). 실패는 모두 `InvalidTokenException` → 401 `ErrorResponse` 한 가지 메시지 |
| 비밀번호 | `@MaxUtf8Bytes(72)` 커스텀 제약을 `RegisterRequestDto.password`에 추가(기존 8~64자 유지) |

## 선택과 이유
1. **필터 등록 방식: `new`로 생성해 SecurityConfig에서만 체인에 추가 (빈 아님)**
   - 선택지: (A) `@Component` (B) `@Component` + `FilterRegistrationBean`으로 자동 등록 비활성화 (C) 빈 없이 `new`
   - A는 Spring Boot가 `Filter` 타입 빈을 서블릿 필터로 자동 등록해, 시큐리티 체인 안(`addFilterBefore`)과 밖에서 두 번 실행될 수 있다(OncePerRequestFilter가 중복 실행은 막아도 체인 밖 실행 위치가 시큐리티 컨텍스트 정리·예외 처리 순서와 어긋난다). B는 보일러플레이트가 늘고 설정을 빼먹으면 A와 같아진다. **C 선택**: 의존성은 `JwtTokenProvider` 하나라 `new`로 충분하고 실수할 여지가 없다. 트레이드오프: 필터 자체를 빈으로 주입/목킹할 수 없다 → 단위 테스트에서 `new JwtAuthenticationFilter(provider)`로 직접 만든다. `JwtSecurityChainTest.filterIsNotABean`이 "빈이 아님"을 검사한다.
2. **필터가 DB를 조회하지 않음** — decisions.md 참고(트레이드오프: 삭제/권한 변경이 access 만료까지 반영 안 됨).
3. **refresh 비회전·미저장** — decisions.md/backlog.md 참고.
4. **`AuthService` 시그니처 변경(범위 내 변경):** `login`이 `LoginResponseDto`를 반환하고 생성자에 `JwtTokenProvider`가 추가됐다. 토큰 발급은 role이 필요한데 기존 `MemberResponseDto`엔 role이 없어서 컨트롤러/별도 클래스로 빼는 것보다 서비스가 `Member`를 들고 발급하는 편이 자연스럽다. 비용: 아래 기존 테스트 3개 수정.

## CORS: Authorization 헤더
- 현재 설정은 `setAllowedHeaders(List.of("*"))`(`SecurityConfig`). Spring의 `CorsConfiguration`은 `*`이면 preflight의 `Access-Control-Request-Headers`를 그대로 허용 응답에 되돌려 준다(브라우저 fetch 스펙의 와일드카드와 달리 `Authorization`도 포함됨). `JwtSecurityChainTest.corsAllowsAuthorizationHeader`에서 `authorization,content-type` preflight가 200이고 `Allow-Headers`에 authorization이 들어 있음을 확인. **수정 불필요.** `allowCredentials`는 여전히 false(Bearer 헤더 방식이라 불필요).

## BCrypt 72바이트 확인 결과 (Spring Security 6.4.2, 테스트로 확인)
- `encode`: 72바이트 초과 입력을 **예외 없이 뒤를 잘라** 해시한다(`"a"*72 + 꼬리`가 `"a"*72`의 해시와 matches=true).
- `matches`: 매우 긴 입력(한글 64자=192바이트)도 예외 없이 true/false만 반환.
- 로그인: 아이디가 있든 없든(더미 해시 비교 포함) 긴 비밀번호는 `InvalidCredentialsException` → 401, 같은 메시지. **로그인 쪽은 조치 불필요**(`BcryptLongPasswordTest`, `AuthTokenControllerTest.veryLongPasswordLoginIsUniform401`).
- 가입: 잘림이 실제 문제라 입력 검증으로 막았다. 한글 24자(72바이트) 통과, 25자(75바이트) 400, 영문 64자 통과. 메시지: "비밀번호가 너무 깁니다. 72바이트 이하여야 합니다 (영문·숫자·기호는 1글자 1바이트, 한글은 1글자 3바이트라 24자까지)."
- 한계: 이 동작은 라이브러리 버전에 의존한다(다른 버전은 예외를 던질 수 있음). 이번 결론은 6.4.2 기준이다.

## 테스트
- 신규 40개 (`./gradlew test` 전체 **138개 통과, 실패 0, 스킵 0**, 기존 98 + 신규 40):
  - `JwtTokenProviderTest`(11): 왕복, 만료, 서명 변조, payload 바꿔치기, 다른 시크릿, type 불일치(양방향), type claim 없음, subject/role 값 오류, alg=none, 깨진 문자열/null/빈 값
  - `JwtPropertiesTest`(5): 정상 / 시크릿 누락 / 32바이트 미만 / BASE64 아님(메시지에 값 없음 확인) / 만료 시간 오류 — `ApplicationContextRunner`로 컨텍스트 로딩 실패 확인
  - `JwtAuthenticationFilterTest`(5): 유효 access → `AuthenticatedMember`, 헤더 없음·`Basic`·접두어 없음·깨짐·만료·다른 시크릿·변조·alg=none·refresh 토큰(13가지) → 인증 없음+체인 계속+예외 없음, provider가 예외를 던져도 통과, 직접 만든 type=refresh 토큰
  - `JwtSecurityChainTest`(5): 실제 필터 체인에서 `@AuthenticationPrincipal AuthenticatedMember` 주입, refresh는 익명, 위조 토큰이 담겨도 permitAll 경로 200(500/401 아님), CORS preflight가 authorization 허용, 필터는 빈이 아님
  - `AuthTokenControllerTest`(11): 실제 AuthService+BCrypt+가짜 시크릿 provider로 — 로그인 응답 토큰 포함/password·해시 없음, 로그인 실패 401 동일, refresh 성공(refreshToken 필드 없음), access 토큰 → 401, 위조·만료·쓰레기 토큰 → 401(응답에 토큰 미포함), 회원 삭제됨 → 401, 빈 토큰 → 400, 비밀번호 바이트 검증 3개(한글 24자 통과 / 25자 400 / 영문 64자 통과), 긴 비밀번호 로그인 401
  - `BcryptLongPasswordTest`(3): 위 BCrypt 동작 확인
- **기존 테스트 수정 3개 (필요한 최소한):**
  - `CorsConfigTest`, `AuthControllerTest`: `SecurityConfig`가 `JwtTokenProvider` 빈을 필요로 하므로 `@MockitoBean JwtTokenProvider` 한 줄 추가. (mock은 `Optional` 반환 시 `Optional.empty()`를 주고, 이 테스트들은 토큰을 보내지 않는다)
  - `AuthControllerTest`/`AuthServiceTest`: `login` 반환 타입과 생성자 변경에 맞춘 컴파일 수정(`LoginResponseDto`, `TestJwt.provider()` 주입). 검증 내용은 그대로.
  - `HighlightRepositoryTest`는 수정하지 않았다.
- 테스트용 jwt 값은 테스트 코드(`security/TestJwt`) 안의 명백한 가짜 문자열로만 만든다. `src/test/resources`에 yml을 만들지 않았다(main 설정을 가리지 않음).

## 실제 설정 컨텍스트 / DB 확인 (테스트 결과 XML 기준, 값은 출력하지 않음)
- `DemoApplicationTests`(풀 컨텍스트, 로컬 yml 사용)는 **통과**. `JwtProperties.afterPropertiesSet` 검증과 `JwtTokenProvider` 생성(시크릿 디코딩·키 생성)을 통과해야 컨텍스트가 뜨므로, 로컬 `application-api-key.yml`의 `jwt.*` 값이 정상(시크릿 BASE64 32바이트 이상, 만료 양수)임이 간접 확인됐다. 키 이름 세 개(`jwt.secret`, `jwt.access-token-expiration`, `jwt.refresh-token-expiration`)가 파일에 있는 것은 키 이름만 추출해 확인.
- DB: `org.postgresql.jdbc.PgConnection` 풀 2개, `Database version: 17.6`(PostgreSQL) → `DemoApplicationTests`와 `HighlightRepositoryTest`는 **여전히 실제 PostgreSQL(Supabase 추정)에 붙는다.** H2는 `HighlightFilterRepositoryTest` 1개만. 새 슬라이스 테스트(`@WebMvcTest`)는 DB에 붙지 않는다. 이번 task는 엔티티를 바꾸지 않아 스키마 변경은 없다.

## DoD
- [x] jjwt 0.12.6 추가, 버전 기록
- [x] jwt.* 설정 읽기, 누락/짧은 시크릿 시 시작 실패(값 미노출), 기본값 없음
- [x] JwtTokenProvider(type claim, 서명/만료/형식/type 검증, 토큰 값 미로깅)
- [x] AuthenticatedMember principal, JwtAuthenticationFilter(access만, 예외 없이 통과, DB 조회 없음, 빈 아님)
- [x] SecurityConfig: STATELESS + 필터만 추가, CORS·CSRF·permitAll·PasswordEncoder 유지
- [x] 로그인 응답 토큰 추가(비밀번호/해시 없음), 로그인 실패 동작 유지
- [x] POST /api/auth/refresh (refresh만, 401 ErrorResponse, 회원 확인, 비회전)
- [x] 비밀번호 UTF-8 72바이트 검증 + 긴 비밀번호 로그인 결과 확인
- [x] decisions.md/backlog.md 추가, `./gradlew test` 138개 통과
- [x] 커밋·push 없음, 범위 밖 변경 없음

## 변경 파일
- 수정: `demo/build.gradle`, `config/SecurityConfig.java`, `service/AuthService.java`, `controller/AuthController.java`, `dto/request/RegisterRequestDto.java`, `exception/GlobalExceptionHandler.java`, 테스트 `CorsConfigTest.java`·`AuthControllerTest.java`·`AuthServiceTest.java`, `.claude/decisions.md`(추가), `.claude/backlog.md`(추가)
- 신규(main): `config/JwtProperties.java`, `security/{AuthenticatedMember,TokenType,JwtTokenProvider,JwtAuthenticationFilter}.java`, `validation/{MaxUtf8Bytes,MaxUtf8BytesValidator}.java`, `dto/request/RefreshRequestDto.java`, `dto/response/{LoginResponseDto,AccessTokenResponseDto}.java`, `exception/InvalidTokenException.java`
- 신규(test): `security/{TestJwt,JwtTokenProviderTest,JwtAuthenticationFilterTest,JwtSecurityChainTest}.java`, `config/JwtPropertiesTest.java`, `controller/AuthTokenControllerTest.java`, `service/BcryptLongPasswordTest.java`
- 신규(문서): 이 파일

---

## 정리
### 요약
로그인이 access/refresh JWT를 발급하고, `JwtAuthenticationFilter`가 access 토큰만 인증으로 인정해 `AuthenticatedMember`를 SecurityContext에 넣는다. 토큰이 없거나 잘못돼도 막지 않고 통과시킨다(`permitAll` 유지). `/api/auth/refresh`는 refresh 토큰만 받아 새 access 토큰을 준다. JWT 설정이 잘못되면 앱이 시작되지 않고, 가입 비밀번호는 UTF-8 72바이트 이하로 제한했다.

### 1. 문제 상황
- 같은 키·구조의 refresh 토큰이 access 토큰으로 통과할 수 있었다(12번 발견 6번).
- 필터를 잘못 등록하면 체인 밖에서 한 번 더 실행되고, 필터에서 예외가 나면 permitAll 경로도 500이 될 수 있다.
- BCrypt가 72바이트 이후를 무시해, 한글 비밀번호는 64자 이내여도 뒷부분이 인증에 쓰이지 않는다.

### 2. 고려한 선택지
- 토큰 구분: type claim / 키 분리 / 발급 시 서버 저장
- 필터 등록: `@Component` / `@Component`+`FilterRegistrationBean` / `new`
- 필터 DB 조회: 매 요청 조회 / claim만 사용
- refresh: 회전+서버 저장 / 비회전·미저장

### 3. 선택 이유
- type claim: 키 하나로 코드가 단순하고 서명 검증 후 type 한 줄로 막는다. 대신 type 검사를 빼먹으면 뚫리므로 `parse(token, expectedType)`로 강제했다.
- `new` 등록: 이중 등록 가능성 자체를 없앤다. 대신 필터를 빈으로 주입할 수 없다.
- claim만 사용: DB 부하 없음, stateless. 대신 삭제/권한 변경이 만료까지 반영되지 않는다.
- 비회전·미저장: 구현이 단순. 대신 로그아웃·폐기 불가(백로그).

### 4. 막혔던 부분과 해결
- 파일 생성 셸 명령이 task 13과 같은 이유로 실패(따옴표/heredoc) → 파일 쓰기 도구로 생성.
- `JwtSecurityChainTest`의 테스트 전용 컨트롤러가 슬라이스에 등록되지 않아 404가 났는데, 전역 핸들러가 이를 500으로 바꿔서 처음엔 필터 문제처럼 보였다 → 로그의 `NoResourceFoundException`으로 원인을 구분하고 `@Import`로 등록. (아래 발견 사항 1과 연결)

## 발견 사항 (범위 밖, 기록만)
1. **404가 500으로 응답된다.** `GlobalExceptionHandler.java:73`의 `Exception` 핸들러가 `NoResourceFoundException`(없는 경로)까지 잡아 500을 돌려준다(이번 테스트 중 실제로 관찰). 본문이 비었거나 JSON이 깨진 요청(`HttpMessageNotReadableException`)도 같을 가능성이 있으나 **미확인**. 같은 줄 근처 `:75`의 `e.printStackTrace()`도 기존 항목(13번 발견 8).
2. **refresh를 서버가 저장하지 않는다.** `AuthService.java`의 `refresh`: 탈취된 refresh를 폐기할 수 없고 만료까지 유효. 회전도 없음(백로그에 기록).
3. **위조/만료 토큰마다 WARN 로그.** `JwtTokenProvider.java:94`, `JwtAuthenticationFilter.java:41`. 인증 없는 사용자가 로그를 부풀릴 수 있다(백로그에 기록). 토큰 값은 남기지 않는다.
4. **`/api/auth/refresh`·`/login`에 호출 제한이 없다.** brute force/남용 방어 없음(기존 13번 항목과 같은 맥락).
5. **access 토큰의 role/username은 발급 시점 값.** 필터가 DB를 조회하지 않으므로 `JwtAuthenticationFilter.java`의 principal은 만료 전까지 stale일 수 있다(의도된 트레이드오프, decisions.md). refresh 시에는 DB의 현재 값으로 새 access를 만든다(`AuthService.refresh`).
6. **13번 backlog "비밀번호 64자 vs 72바이트" 항목은 이번에 해결됨.** backlog.md는 덮어쓰지 않는 규칙이라 원문은 남겼다. 체크는 사용자가 직접.
7. **테스트 DB 분리 문제가 그대로.** `DemoApplicationTests`·`HighlightRepositoryTest`가 실제 PostgreSQL에 붙는다(13번 발견 1).
8. **작업 중 `application.yml` 일부 줄이 내 화면에 출력됨** (위 "보고할 실수"). 추적 파일에는 없음.

## 다음 task 제안 (task 15 또는 16)
- **task 15 (권장 선행): 프론트 로그인 연동** — 이번에 토큰이 생겼으므로 로그인 화면/토큰 저장/Authorization 헤더 부착/refresh 재시도. 프론트 변경이라 별도 task. (프록시 방식이면 Authorization 헤더 전달 확인 필요: 프론트 호출이 프록시와 8080 직접 두 갈래임, backlog 참고)
- **task 16: Content↔Member 연결** — `Content.userId`를 `Member.id`로 연결(FK 여부 결정, 기존 `user_id=1` 행 보정, 스키마 반영 수단 결정 필요: 13번 발견 6). 이후 17에서 `CalendarController`의 `userId = 1L`을 `@AuthenticationPrincipal AuthenticatedMember`의 `id()`로 교체.
- 사전 결정: 테스트 DB 분리, 스키마 반영 수단(Flyway 등), refresh 저장 방식.

## 복습 질문

<details><summary>Q1. 토큰에 type claim이 없으면 어떤 문제가 생기고, 이번에 어떻게 막았나?</summary>

문제: access와 refresh를 같은 키로 서명하면 둘 다 "서명이 유효한 토큰"이라, 유효기간이 긴 refresh 토큰을 `Authorization: Bearer`에 넣어 access처럼 쓸 수 있다(refresh를 탈취당하면 긴 시간 API 접근이 가능해지고 access를 짧게 둔 의미가 사라진다). 반대로 access 토큰을 refresh 엔드포인트에 내밀어 새 토큰을 계속 받는 것도 막을 수 없다(task 12 발견 6번).
해결: 두 토큰에 `type`(access/refresh) claim을 넣고, `JwtTokenProvider.parse(token, expectedType)`가 서명·만료 확인 뒤 type이 기대와 다르면 거부한다. 필터는 `TokenType.ACCESS`만, `AuthService.refresh`는 `TokenType.REFRESH`만 요청한다. type claim이 없는 토큰도 거부된다. 근거 테스트: `JwtTokenProviderTest.typeMismatch`·`missingTypeClaim`, `JwtAuthenticationFilterTest`(refresh 토큰 → 인증 없음), `AuthTokenControllerTest.refreshRejectsAccessToken`(401).
</details>

<details><summary>Q2. 필터가 실패 시 예외를 던지지 않고 통과시키는 이유는? OncePerRequestFilter를 @Component로 두면 어떤 문제가 생길 수 있고, 어떤 등록 방식을 골랐나?</summary>

통과시키는 이유: 지금은 `anyRequest().permitAll()`이라 인증이 필요 없는 경로가 대부분인데, 만료·위조 토큰을 필터에서 막거나 예외를 던지면 공개 API(검색 등)까지 401/500이 된다. 필터의 일은 "토큰이 유효하면 인증 정보를 채우는 것"까지이고, "인증이 없으면 막는다"는 인가 규칙(task 19의 `authenticated`)이 맡는다. 그래서 어떤 실패든 SecurityContext를 비워 둔 채 `chain.doFilter`를 호출하고, `chain.doFilter`는 try 밖에 둬서 다음 단계의 예외를 삼키지 않는다.
@Component 문제: Spring Boot는 `Filter` 타입 빈을 서블릿 컨테이너 필터로 자동 등록한다. 그러면 `addFilterBefore`로 시큐리티 체인에 넣은 것과 별개로 체인 밖에서도 실행되어, 순서가 시큐리티 컨텍스트 처리와 어긋나거나 이중 실행될 수 있다(OncePerRequestFilter는 같은 요청의 재실행을 막지만, 체인 밖 실행 자체가 의도와 다르다).
선택: 빈으로 만들지 않고 `SecurityConfig.filterChain`에서 `new JwtAuthenticationFilter(jwtTokenProvider)`로 만들어 체인에만 추가했다. 트레이드오프는 필터를 빈으로 주입·목킹할 수 없다는 점이고, 테스트에서는 `new`로 직접 만든다. `JwtSecurityChainTest.filterIsNotABean`이 빈이 아님을 검사한다.
</details>

<details><summary>Q3. (기존 코드 연결) CalendarController의 userId = 1L을 task 17에서 AuthenticatedMember로 어떻게 바꾸고, 필터가 DB를 조회하지 않아서 생기는 트레이드오프는?</summary>

교체 방법: 컨트롤러 메서드 파라미터에 `@AuthenticationPrincipal AuthenticatedMember member`를 받고 `userId = member.id()`를 서비스로 넘긴다. 이번에 필터가 토큰의 subject(Member.id, Long)로 principal을 채우므로 기존 `Content.userId`(Long)와 타입이 바로 맞는다(`JwtSecurityChainTest`의 Probe 컨트롤러가 같은 방식으로 id를 받는다). 단, 이 시점에는 SecurityConfig를 `authenticated`로 바꿔야(task 19) principal이 null일 때 NPE 없이 401로 막을 수 있다. 그 전에는 토큰 없는 요청이 `member == null`이므로 task 17에서 null 처리 방침(임시 허용 또는 task 19와 함께)을 정해야 한다. `Content.userId`의 `@Builder.Default 1L`(decisions 2026-10-20)도 이때 제거한다.
트레이드오프: 필터가 요청마다 DB를 조회하지 않고 토큰 claim만 쓰므로 DB 부하가 없고 stateless지만, 회원이 삭제되거나 role·username이 바뀌어도 access 토큰이 만료될 때까지는 이전 정보로 인증된다. 그래서 `userId`로 데이터를 조회/저장하는 쪽은 "존재하지 않는 userId"가 올 수 있음을 전제로(FK 제약 등) 해야 하고, access 만료를 짧게 두며, 회원 존재 확인이 꼭 필요한 refresh에서만 DB를 조회한다.
</details>
