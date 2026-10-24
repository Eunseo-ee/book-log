# 12. feat/security 머지 가능성 및 기존 데이터 연결 지점 조사

- 브랜치: `main` (조사 시점. `HEAD = origin/main = 1a0e866`, 작업 트리 깨끗)
- 참고: `.claude/decisions.md` 2026-10-20 "Content.userId는 @Builder.Default로 1L 유지 (JWT 병합 시 교체)", 2026-10-21 CORS 전역 설정, `.claude/tasks/04-calendar-investigation.md`, `03-tmdb-author-null.md`

## 목표
`origin/feat/security`(로그인/JWT)를 main에 합치기 전에, (A) 머지가 되는지, (B) 인증 코드가 어떤 상태인지, (C) 로그인 사용자와 기존 데이터를 어디서 연결해야 하는지를 코드로 조사한다.
판단은 "확인(코드)", "추정", "미확인"으로 나누고 근거를 파일:줄로 남긴다. 코드는 수정하지 않는다.

## 범위
- 수정 허용: 이 파일
- 범위 밖: 코드·설정·테스트·기존 task 파일 수정, 커밋, push, 해결 방안 확정
- 머지 시도는 `git merge --no-commit --no-ff` 후 `git merge --abort`로만 했다.
- 요청서 마지막 문장("발견한 문제는 고치지 말고 … 파일:줄과")이 중간에서 끊겨 있었다. 발견 사항에는 파일:줄과 근거를 적는 것으로 해석했다.

---

## 조사 결과 요약
| 항목 | 판단 | 한 줄 근거 |
|---|---|---|
| A. 머지 | **충돌 4개** (내용 충돌 2, modify/delete 2) | `SecurityConfig.java`, `build.gradle`은 내용 충돌. `application.yml`, `.vscode/settings.json`은 main이 삭제한 파일을 security가 수정함 |
| A. TMDB null 커밋 중복 | **중복 아님. feat/security에 해당 커밋이 없다** | `048b1f9`는 feat/security의 조상이 아니고, security 쪽 변경에 `TmdbSearchService`·테스트가 없다 |
| B. 인증 흐름 | 로그인 → 토큰 발급 → 필터 검증 → DB 재조회까지 구현됨 | 토큰 subject는 **username**이고 id가 없다 |
| B. refresh | **발급만 한다.** 재발급/검증 엔드포인트 없음 | `AuthController`에 register/login뿐 |
| B. 시크릿 | **`application.yml`에 리터럴로 하드코딩**, 그 파일이 security 브랜치에서 git 추적됨 | main은 이 파일을 gitignore + 추적 해제한 상태 |
| C. 연결 지점 | `userId`를 쓰는 곳 5곳. **타입 불일치**: `Member.id`는 UUID, `Content.userId`는 Long | 연결하려면 타입 결정이 먼저 필요 |
| C. 사용자 구분 없는 쿼리 | 캘린더 1개만 user_id로 거르고 **나머지 전부 구분 없음** | 중복 체크·isSaved·통계·filter·하이라이트 전부 |
| C. 하이라이트 소유권 | **없다.** `Highlight`에 사용자 정보 자체가 없음 | 수정/삭제는 id만으로 동작 |
| 테스트 | **86개 전부 통과** (단, 로컬 `application.yml`이 없는 상태에서 실행. 발견 사항 1번) | `./gradlew test` BUILD SUCCESSFUL |

---

## A. 머지 가능성

### 1) 브랜치 관계 (`git fetch` 후)
- merge-base: `4a03dd5` ("Merge pull request #10 from Eunseo-ee/feature/9-statistics")
- 로컬 `main`과 `origin/main`은 같다(`1a0e866`). 로컬만 있는 커밋, origin에만 있는 커밋 없음.
- **feat/security에만 있는 커밋 5개** (모두 NIsik419, 2026-05-02)
  | 커밋 | 내용 |
  |---|---|
  | `0006231` | Member 엔티티·레포지토리, DTO, `build.gradle`(jjwt), `application.yml`(jwt 블록), `.vscode/settings.json` |
  | `41418f9` | JwtTokenProvider, JwtAuthenticationFilter, CustomUserDetailsService |
  | `c5ba07c` | AuthController, AuthService (회원가입/로그인) |
  | `7c90003` | SecurityConfig를 JWT 구조로 교체 |
  | `6f57a9b` | `docs/*.md` 5개 추가 (architecture, code-guide, optimization, tech-decisions, troubleshooting) |
- **main에만 있는 커밋 약 40개** (merge-base 이후 전부). 검색 UI, Redis 캐시, 평점/TMDB null 수정, 캘린더 수정, CORS 전역 설정, 하이라이트 조회/추가/수정 UI, 영화/드라마 탭 분리 등. 변경 규모는 87개 파일, +8258/-272.
- security 쪽은 20개 파일 변경이고, main과 **같은 파일을 건드린 것은 4개**뿐이다.

### 2) 시험 머지 결과
- 시작 전 `git status`: `nothing to commit, working tree clean`
- `git merge --no-commit --no-ff origin/feat/security` → `Automatic merge failed`
- 충돌 파일 4개
  | 파일 | 상태 |
  |---|---|
  | `demo/src/main/java/book_log/demo/config/SecurityConfig.java` | 내용 충돌 (UU) |
  | `demo/build.gradle` | 내용 충돌 (UU) |
  | `demo/src/main/resources/application.yml` | modify/delete (DU): main이 삭제, security가 수정 |
  | `.vscode/settings.json` | modify/delete (DU): main이 삭제, security가 수정 |
- 충돌 없이 자동으로 들어오는 파일(추가 `A`)은 Member, Role, DTO 3개, MemberRepository, security 패키지 3개, AuthService, AuthController, `docs/*.md` 5개다.
- `git merge --abort` 실행(exit 0) 후: `MERGE_HEAD` 없음, `HEAD = 1a0e866`, `git status`: `nothing to commit, working tree clean`
- **abort 후 `demo/src/main/resources/application.yml`이 작업 트리에서 사라졌다.** 발견 사항 1번 참고.

### 3) 충돌 파일별로 양쪽이 바꾼 것
**`SecurityConfig.java`** (main: 전역 CORS, security: JWT)
| | main (`config/SecurityConfig.java`) | feat/security |
|---|---|---|
| 클래스 | `@Value`로 `app.cors.allowed-origins` 주입(`:22-23`). `@RequiredArgsConstructor` 없음 | `@RequiredArgsConstructor` + `JwtTokenProvider` 필드 주입 |
| PasswordEncoder | 없음 | `BCryptPasswordEncoder` 빈 |
| CORS | `.cors(Customizer.withDefaults())`(`:33`) + `CorsConfigurationSource` 빈(`:49-60`, `/api/**`, GET/POST/PUT/DELETE/OPTIONS, 헤더 `*`, maxAge 3600) | **CORS 설정 없음** |
| 접근 제어 | `anyRequest().permitAll()`(`:37`) | `/api/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`만 permitAll(`:80-86`), 나머지 `authenticated()`(`:88`) |
| 세션 | 설정 없음 | `STATELESS`(`:76`) |
| 필터 | 없음 | `addFilterBefore(new JwtAuthenticationFilter(...), UsernamePasswordAuthenticationFilter.class)`(`:94`) |
| 헤더 | `frameOptions` disable(`:42`, H2 콘솔용) | 해당 설정 삭제 |

- 충돌 마커는 import 3곳, 필드 선언부, `filterChain` 본문, 파일 끝 `corsConfigurationSource` 빈, 모두 5곳이다. 양쪽 변경이 **서로 배타적이지 않고 합칠 수 있는 성격**이다(CORS는 `.cors()` 한 줄 + 빈, JWT는 세션/인가/필터). 다만 한쪽을 통째로 고르면 다른 쪽 기능이 사라진다.
- **텍스트 충돌이 아닌 의미상 겹침 (코드 기준, 미실행)**
  - security 쪽 체인에는 `.cors()`가 없고 `anyRequest().authenticated()`다. 그대로 두면 브라우저의 preflight `OPTIONS /api/...`가 인증 없이 들어와 거부된다. 04, 07에서 겪은 CORS 문제가 다시 생길 수 있다.
  - `CorsConfigTest`(`@WebMvcTest` + `@Import(SecurityConfig.class)`, `config/CorsConfigTest.java:32-33`)는 `JwtTokenProvider` 빈이 없으면 컨텍스트가 뜨지 않을 것으로 보인다(`@MockitoBean` 목록: `:42`, `:45`는 서비스 두 개만 막고 있음). 이 테스트는 `permitAll` 전제로 만들어졌다.

**`demo/build.gradle`**
- main: `ext['lombok.version'] = '1.18.42'` 추가, `spring-boot-starter-data-redis`, `spring-boot-starter-cache` 추가
- security: `jjwt-api/impl/jackson 0.12.3` 3줄 추가
- 같은 위치(의존성 블록의 같은 지점)에 양쪽이 줄을 추가해서 충돌한 것이다. **서로 겹치는 의존성은 없다.**
- `spring-boot-starter-security`는 이미 base에 있다(`build.gradle` 현재 파일 기준).

**`application.yml`** (modify/delete)
- main: `2cc8a22 chore: application.yml git 추적 해제`. `.gitignore:39`에 등록됨
- security: base(`846d2d4`)의 `application.yml`에 `jwt:` 블록 4줄(secret, access/refresh 만료)을 **추가해서 커밋**했다.
- 이 파일이 충돌 목록에 있다는 것은, 머지를 확정하면 **삭제를 유지할지(추적 해제) security 버전을 되살릴지** 정해야 한다는 뜻이다. 되살리면 시크릿이 다시 추적된다.

**`.vscode/settings.json`** (modify/delete)
- main: `.gitignore:37` `.vscode/`로 추적 해제. security: `"java.compile.nullAnalysis.mode": "automatic"` 1줄 추가. 기능과 무관한 에디터 설정이다.

### 4) TMDB 저자/감독 null 처리 커밋과의 중복 — 중복 아님
- `048b1f9 fix: TMDB 저자/감독을 문자열 대신 null로 내려 DB 오염 방지`는 **feat/security에 없다** (`git merge-base --is-ancestor 048b1f9 origin/feat/security` → false).
- security 쪽 5개 커밋이 건드린 `service/` 파일은 `AuthService.java`(신규) 하나뿐이고, 테스트 파일은 하나도 없다(`git diff --stat 4a03dd5 origin/feat/security -- service test`).
- 즉 security 브랜치는 03번 작업 이전의 main(PR #10 시점)에서 갈라진 것이라, 03번 작업은 main에만 있고 머지 때 충돌하지 않는다.
- 요청서의 "feat/security의 TMDB 저자/감독 null 처리 커밋"에 해당하는 커밋은 찾지 못했다. 다른 브랜치나 로컬 커밋을 말씀하신 것이라면 확인이 필요하다(**미확인**).

---

## B. 인증 코드 현황 (`origin/feat/security` 기준)

### 5) 로그인 → 토큰 발급 → 요청 인증 흐름
경로는 `demo/src/main/java/book_log/demo/` 아래다.

**로그인 (토큰 발급)**
1. `controller/AuthController.java:68-72` `POST /api/auth/login` → `@Valid LoginRequestDto`(username, password `@NotBlank`)
2. `service/AuthService.java:71` `login()`: `memberRepository.findByUsername`(`:73`) → 없으면 `IllegalArgumentException("존재하지 않는 사용자입니다.")`
3. `passwordEncoder.matches(...)`(`:78`) → 불일치면 `IllegalArgumentException("비밀번호가 일치하지 않습니다.")`
4. `JwtTokenProvider.generateAccessToken/generateRefreshToken(username)`(`:83-84`) → `TokenResponseDto(accessToken, refreshToken)` 반환

**토큰이 있는 요청 (인증)**
1. `SecurityConfig`가 `JwtAuthenticationFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 등록(`SecurityConfig.java:94`)
2. `security/JwtAuthenticationFilter.java:50` `resolveToken`: `Authorization: Bearer ...`에서 토큰 추출(`:74-81`)
3. `:53` `validateToken`(`JwtTokenProvider.java:134-148`): 서명·만료·형식 검증, 실패는 `false`(로그만 남김)
4. `:55` `getAuthentication`(`JwtTokenProvider.java:159-165`) → `getUsername`(`:120`, subject) → `CustomUserDetailsService.loadUserByUsername`(`security/CustomUserDetailsService.java:41-54`)이 **요청마다 DB 조회** → `UsernamePasswordAuthenticationToken(userDetails, null, authorities)`
5. `:58` `SecurityContextHolder`에 저장 후 `:62` 다음 필터로. 토큰이 없거나 무효여도 그냥 통과시키고, 접근 제어는 `SecurityConfig`의 `authenticated()`가 한다.

| 클래스 | 역할 |
|---|---|
| `AuthService` | 회원가입(중복 확인→BCrypt→저장, `:42-57`), 로그인(조회→matches→토큰 발급, `:71-87`) |
| `JwtTokenProvider` | 토큰 생성(`buildToken` `:102-112`)·검증·파싱. 서명 키와 만료 시간을 생성자에서 주입받음(`:56-67`) |
| `JwtAuthenticationFilter` | 요청마다 1회 토큰을 꺼내 검증하고 SecurityContext를 채움. `OncePerRequestFilter`이고 **빈이 아니라 `SecurityConfig`가 `new`로 만든다** |
| `CustomUserDetailsService` | username으로 `Member`를 읽어 Spring `User`로 변환. **권한만 담고 `Member.id`는 담지 않는다**(`:50-54`) |

- 토큰 payload는 `sub`(username), `iat`, `exp`뿐이다(`JwtTokenProvider.java:106-111`). **userId(Member.id)가 토큰에도, `SecurityContext`의 principal에도 없다.** 컨트롤러가 `@AuthenticationPrincipal`을 받아도 얻는 것은 username 문자열이 든 Spring `User`다.

### 6) 비밀번호 암호화
- BCrypt. `SecurityConfig`의 `BCryptPasswordEncoder` 빈(security 쪽 `PasswordEncoder passwordEncoder()`), `AuthService.java:51` `passwordEncoder.encode(...)`, `:78` `matches(...)`.
- main의 `SecurityConfig`에도 `PasswordEncoder` 빈이 없다(`config/SecurityConfig.java` 전체). 이 빈은 security 쪽에서만 들어온다.
- 회원가입 비밀번호 규칙은 8자 이상(`SignupRequestDto.java` `@Size(min = 8)`), 아이디 4~20자, 이메일 `@Email`. 비밀번호 상한은 없다.

### 7) JWT 시크릿 키와 만료 시간
- 정의 위치: **`demo/src/main/resources/application.yml`의 `jwt:` 블록**(security 브랜치 기준 20~23번째 줄 부근)
  - `jwt.secret`: **리터럴 값이 직접 적혀 있다** (환경변수 `${...}` 치환 아님). 값은 이 문서에 적지 않는다.
  - `jwt.access-token-expiration: 900000` (15분, ms)
  - `jwt.refresh-token-expiration: 604800000` (7일, ms)
- 주입: `JwtTokenProvider.java:57-59` `@Value("${jwt.secret}")` 등. 시크릿은 BASE64 디코딩(`:63`)해서 HMAC 키로 쓴다. 기본값이 없어서 `jwt.*`가 없으면 앱이 뜨지 않는다.
- 같은 파일의 `spring.datasource.url/username`도 리터럴이다(값 마스킹 확인, 출력하지 않음). 비밀번호 키는 이 파일에 없고 `application-api-key.yml` 쪽으로 보인다(**미확인**, 해당 파일은 열지 않음).
- **gitignore 여부**
  - main: `.gitignore:39-40`에 `application.yml`, `application-api-key.yml`이 모두 있다. main에서는 추적되지 않는다.
  - feat/security: `application.yml`이 **git에 추적되고 있고, jwt 시크릿이 커밋 `0006231`에 들어 있다.** 이 커밋이 이미 원격(`origin/feat/security`)에 올라가 있으므로 원격 이력에 시크릿이 남아 있다(코드 기준 확인).

### 8) refresh 토큰
- **발급만 한다.** 로그인 응답에 포함(`AuthService.java:84`, `TokenResponseDto`)될 뿐이다.
- 재발급 엔드포인트 없음: `AuthController`의 매핑은 `/register`, `/login` 두 개뿐이다.
- 별도 저장(DB/Redis)·폐기·로그아웃 없음. `Member`에도 refresh 관련 필드가 없다.
- access와 refresh는 **같은 키·같은 구조·만료만 다르다**(`buildToken`). 토큰 종류를 구분하는 claim이 없어서, `JwtAuthenticationFilter`는 refresh 토큰도 access 토큰처럼 받아 준다(`:53`, 코드 기준).
- `docs`/javadoc에는 "accessToken 만료 시 재발급용"이라고 적혀 있으나(`JwtTokenProvider.java:23`, yml 주석) 그 기능은 구현되어 있지 않다.

### 9) SecurityConfig의 permitAll 범위
| | main | feat/security |
|---|---|---|
| 열려 있음 | **모든 경로**(`anyRequest().permitAll()`, `:37`) | `/api/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**` |
| 인증 필요 | 없음 | 그 외 전부(`anyRequest().authenticated()`, `:88`) |
- security 기준으로 인증이 필요해지는 경로: `/api/search`, `/api/contents/**`(저장·check·filter), `/api/highlights/**`, `/api/calendar/**`, `/api/statistics`, `/actuator/**`(starter-actuator가 build.gradle에 있다).
- `AuthenticationEntryPoint`/`AccessDeniedHandler` 설정이 없다. 이 경우 미인증 응답은 401이 아니라 **403이 될 가능성이 크다**(Spring Security 6 기본 동작 기준 **추정**, 미실행). javadoc은 401이라고 적었다(`JwtAuthenticationFilter.java:29`).
- 프론트(`frontend/`)에는 `Authorization`, `token`, `login` 등 인증 관련 코드가 **하나도 없다**(grep 결과 0건). 머지 후에는 프론트의 모든 API 호출이 인증 때문에 실패할 것이다(코드 기준).

### 10) Member 엔티티 (`domain/Member.java`)
| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | `UUID` | `@Id`, `GenerationType.UUID`, `columnDefinition = "uuid"`, `updatable = false`, `nullable = false` |
| `username` | `String` | `nullable = false`, `unique = true` |
| `password` | `String` | `nullable = false` (BCrypt 해시가 들어감) |
| `email` | `String` | `nullable = false` (unique 아님) |
| `role` | `Role` (`ROLE_USER`, `ROLE_ADMIN`) | `@Enumerated(STRING)`, `nullable = false` |
- `BaseTimeEntity` 상속(createdAt/modifiedAt 계열), 테이블명 `members`. 생성자는 `@Builder` 하나, 기본 생성자는 `protected`.
- `columnDefinition = "uuid"`는 PostgreSQL 전용 타입명이다. 테스트가 쓰는 임베디드 H2에서 이 엔티티를 만들 때 동작하는지는 **미확인**(H2가 `uuid` 타입을 지원하므로 가능성은 높다고 보지만 실행하지 않음).
- `MemberRepository`: `findByUsername`, `existsByUsername`. `JpaRepository<Member, UUID>`.

---

## C. 기존 데이터와의 연결 지점 (main 기준)

### 11) 임시 사용자 값(`userId = 1L`)이 쓰이는 위치
| 위치 | 내용 |
|---|---|
| `controller/CalendarController.java:34-35` | `Long userId = 1L;` → `calendarService.getMonthlyActivity(userId, ...)`(`:37`). `:32-33` 주석에 `@AuthenticationPrincipal`로 교체하라고 적혀 있음 |
| `domain/Content.java:31-34` | `// 임시 (JWT 병합 시 교체)` `@Column(name = "user_id") @Builder.Default private Long userId = 1L;` |
| `service/CalendarService.java:20,23` | `userId` 파라미터를 받아 `findActiveDays`에 전달(값 자체는 컨트롤러에서 옴) |
| `repository/ContentRepository.java:60,66` | `WHERE c.user_id = :userId` (native 쿼리, 캘린더 전용) |
- `demo/src/main` 전체를 `userId|user_id|1L`로 grep한 결과가 위 4파일이다. 프론트(`frontend/`, node_modules 제외)에는 `userId` 사용이 없다.
- 테스트에서 `1L`은 `CalendarServiceTest` 등 Mockito 테스트의 인자로만 쓰인다(리포지토리 쿼리의 user_id 조건은 어떤 테스트도 실제로 검증하지 않는다. 04번 문서와 같음).

### 12) `Content.userId`의 현재 상태
- 필드: `private Long userId = 1L;` + `@Builder.Default`(`Content.java:31-34`). 04번에서 발견한 "빌더가 초기값을 무시해 null로 저장" 문제는 `d5315eb fix: Content.userId가 빌더 저장 시 null이 되던 문제 수정`으로 **main에서 고쳐졌다**(`@Builder.Default` 있음, 코드 확인).
- `@Builder.Default`의 동작: 빌더로 만들 때 `userId`를 지정하지 않으면 `1L`이 들어간다. 지정하면 그 값이 쓰인다(`ContentRequestDto.toEntity()`는 `.userId(...)`를 호출하지 않으므로 `dto/request/ContentRequestDto.java:46-58` → 항상 1L). 반대로 JPA가 쓰는 `protected Content()`에서도 1L이 대입되므로, 조회 후 엔티티의 초기값도 1L이다(이후 DB 값으로 덮어씀).
- 즉 **지금 새로 저장하는 행은 전부 `user_id = 1`**이다(코드 기준). 04번 조사 때의 빌드 산출물이 아니라 현재 소스 기준이다.
- **기존 DB 행**은 `decisions.md` 2026-10-20대로 "user_id NULL 행은 SQL로 직접 보정"하기로 한 상태다. 실제로 보정했는지, NULL 행이 남았는지는 **미확인**(DB 접속 안 함). 확인용 SQL은 04번 문서의 ①②가 그대로 쓰인다.
- 컬럼 제약: `@Column(name = "user_id")`만 있고 `nullable = false`, FK, 인덱스가 없다.
- **타입 불일치(연결 시 핵심):** `Content.userId`는 `Long`(DB bigint), `Member.id`는 `UUID`. 같은 값으로 연결할 수 없다. `Member`와 `Content`는 JPA 연관관계도 없다.

### 13) 사용자 구분 없이 동작하는 쿼리/서비스 메서드
| 위치 | 동작 | 사용자 조건 |
|---|---|---|
| `ContentRepository.findByExternalIdAndCategory` (`:37`) → `ContentService.isAlreadySaved` (`:30-32`) | 중복 저장 체크(`saveContent` `:59`, `/api/contents/check`) | **없음.** 다른 사용자가 저장한 작품도 "이미 저장됨" |
| `ContentRepository.findByExternalIdIn` (`:43`) → `ContentService.findSavedKeys` (`:35-42`) → `SearchService.java:45` | 검색 결과의 `isSaved` | **없음.** decisions 2026-10-19 "isSaved의 userId 미반영(JWT 병합 시)"에 해당 |
| `ContentRepository.findByYearAndCategory` / `findByYear` / `findByCategory` (`:46-54`), `ContentService.getFilteredContents` (`:71-93`, `findAll()` 포함) | `/api/contents/filter` | **없음.** 조건이 없으면 전체 사용자 데이터를 `findAll()` |
| `ContentRepository.countByCategoryInterface`, `countByGenreInterface`, `getAverageRating`, `countByMonth`, `findMostActiveDay` (`:74-99`) → `StatisticsService.getMonthlyStatistics` (`:25-90`) | 월별 통계 | **없음.** 컨트롤러도 사용자 값을 받지 않음(`StatisticsController.java:26-42`) |
| `HighlightService.getHighlights` (`:76-89`) + `HighlightSpecifications` (`viewYear`, `categoryIn`) | 모음집 조회 | **없음.** 조건은 연도·카테고리뿐. `Highlight`에 사용자 컬럼이 없어 `content.userId`로 조인해서 거르는 방식이어야 함 |
| `HighlightService.saveHighlight` (`:33-51`) | 하이라이트 추가 | contentId로 `findById`만 함. **그 content가 로그인 사용자의 것인지 확인하지 않음** |
| `HighlightService.updateHighlight` (`:54-68`), `deleteHighlight` (`:71-73`) | 수정/삭제 | **없음** (14번) |
| `ContentRepository.findActiveDays` (`:57-69`) → `CalendarService` | 캘린더 | **있음(유일).** 단 값이 컨트롤러의 하드코딩 `1L` |
- `ContentService.saveContent`(`:46-68`)도 `toEntity()`를 그대로 저장해서 소유자를 지정하지 않는다(`Content.java`의 기본값 1L이 들어갈 뿐).
- 중복 체크는 쿼리 `findByExternalIdAndCategory`가 `Optional` 하나를 기대한다. 사용자별로 같은 작품을 따로 저장하게 되면 한 `(externalId, category)`에 행이 여러 개가 되므로, 지금 형태의 메서드는 `IncorrectResultSizeDataAccessException`을 던질 수 있다(코드 기준 추정, 미실행).

### 14) 하이라이트 수정/삭제의 소유권 검사
- **없다.**
  - `HighlightController.java:46-48` `PUT /{id}`, `:51-56` `DELETE /{id}`는 `@PathVariable Long id`만 받는다. 로그인 사용자 정보 파라미터가 없다.
  - `HighlightService.updateHighlight`(`:54-68`)는 `findById(id)` → `highlight.update(...)`. `deleteHighlight`(`:71-73`)는 `deleteById(id)`.
  - `Highlight` 엔티티(`domain/Highlight.java:13-27`)에 사용자·소유자 필드가 없고, `content`(`@ManyToOne LAZY`)로만 간접 연결된다. 소유권은 `highlight.content.userId`로 판정해야 한다.
- 추가로, `deleteHighlight`는 존재하지 않는 id를 지우면 `EmptyResultDataAccessException`이 아니라 Spring Data 3.x 기본 동작(없으면 무시)에 따라 조용히 끝난다(**추정**).
- `updateHighlight`는 `HighlightRequestDto`의 `contentId`를 `@NotNull`로 받지만 사용하지 않는다. 다른 content로 옮길 수는 없다(코드 기준: `update()`가 text/page/season/episode/timestamp만 바꿈).

### 15) 테스트: 실제 DB(Supabase)에 접속하는 것 vs 아닌 것
`./gradlew test` 실행: **BUILD SUCCESSFUL (2m 9s), 86개 통과, 실패 0, 에러 0, 스킵 0** (2026-10-23, main `1a0e866`, 테스트 결과 XML 17개 클래스 합계. `CorsConfigTest` 중첩 클래스 2개 포함)

| 구분 | 테스트 | 근거 |
|---|---|---|
| **실제 DB 설정을 쓰는 것** (로컬 `application.yml`이 있으면 Supabase에 접속) | `DemoApplicationTests` (`@SpringBootTest`, 오버라이드 없음) | 데이터소스를 yml에서 읽음 |
| | `HighlightRepositoryTest` (`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)`, `HighlightRepositoryTest.java:30-31`) | 임베디드 DB로 바꾸지 않겠다고 명시. yml의 데이터소스를 그대로 사용 |
| 임베디드 H2 | `HighlightFilterRepositoryTest` (`@DataJpaTest`, 주석 "기본 Replace.ANY라서 임베디드 H2") | `HighlightFilterRepositoryTest.java:25-26` |
| Spring MVC 슬라이스(DB 없음) | `CorsConfigTest` (`@WebMvcTest` + `@Import(SecurityConfig)`) | 서비스는 `@MockitoBean` |
| 최소 Spring 설정(DB 없음) | `CacheFailureFallbackTest` (`@SpringJUnitConfig`, Redis는 안 쓰는 포트로 연결) | 서버 불필요 |
| Mockito 단위 테스트 | `CalendarServiceTest`, `ContentServiceTest`, `HighlightQueryServiceTest`, `HighlightServiceTest`, `KakaoSearchServiceTest`, `StatisticsServiceTest`, `TmdbSearchServiceTest` (`@ExtendWith(MockitoExtension)`) | 리포지토리·RestTemplate이 Mock |
| 단순 테스트(어노테이션 없음) | `SearchServiceTest`, `TmdbSearchServiceCacheTest`, `ContentRequestDtoTest`, `UnifiedSearchResponseJsonTest` | Spring/DB 미사용 |
- `CLAUDE.md`의 "테스트는 실제 DB(Supabase)에 붙지 않게 임베디드 H2를 쓴다"와 달리, **위 두 테스트는 yml이 있으면 Supabase 설정을 사용한다.** `HighlightRepositoryTest`는 "수정하지 않는다"로 정해진 기존 테스트다.
- **이번 실행의 전제:** 실행 시점에 로컬 `application.yml`이 없었다(발견 사항 1번). 그래서 위 두 테스트도 데이터소스 URL이 없어 임베디드 H2로 돌았다(`DemoApplicationTests` 로그: `HikariPool-1 - Added connection ... url=jdbc:h2:mem:...`, 종료 시 `drop table if exists content/highlight` 로그). **이 상태의 "전부 통과"는 실제 Supabase 연결을 검증한 결과가 아니다.**
- `Member`/security 쪽 테스트는 feat/security에 하나도 없다(변경 파일에 test 경로 없음).

---

## 발견 사항 (범위 밖, 기록만)
1. **[작업 중 발생] 시험 머지 + abort로 로컬 `demo/src/main/resources/application.yml`이 사라졌다.**
   - 이 파일은 gitignore 대상(`.gitignore:39`)이라 원래는 사용자 로컬에만 있던 설정 파일이다. security 브랜치는 이 경로를 추적 파일로 갖고 있어서, `--no-commit` 머지가 로컬 파일 자리에 security 버전을 써 넣었고(수정 시각이 머지 시각으로 바뀐 것을 확인), `git merge --abort`가 그 파일을 지웠다. 확인한 현재 상태: `demo/src/main/resources/`에는 `application-api-key.yml`, `static`, `templates`만 남아 있다.
   - 머지 직전에 이 파일이 있었는지의 증거는 `CLAUDE.md`가 이 파일을 gitignore 대상으로 설명한다는 점뿐이고, **원래 내용은 백업하지 못했다.** `demo/build/resources/main/`에도 복사본이 없다.
   - 머지 중에 읽은 **security 브랜치 버전**의 복사본만 scratchpad에 있다: `C:\Users\2whwh\AppData\Local\Temp\claude\c--Users-2whwh-Downloads-class-schedule-generator\a45441f3-445a-4503-b24e-8dc15a74e874\scratchpad\application.yml.during-merge` (DB 접속 정보와 jwt 시크릿이 들어 있음). 이것은 원래 파일과 같다는 보장이 없고, 복구에 쓰지 않았다.
   - 앞으로 같은 조사를 한다면 `git merge-tree` 같은 작업 트리를 건드리지 않는 방법이나 별도 worktree에서 해야 한다. 요청서가 정한 방식(`--no-commit` 후 `--abort`)은 **gitignore된 로컬 파일과 같은 경로에 추적 파일이 있는 브랜치**에서는 이 부작용이 있다.
2. **JWT 시크릿과 DB 접속 정보가 `origin/feat/security`의 git 이력에 평문으로 있다.** 커밋 `0006231`의 `demo/src/main/resources/application.yml`(`jwt.secret`, `spring.datasource.url/username`). main에서 이 파일을 추적 해제한 작업(`2cc8a22`)과 정반대 방향이다. 머지 충돌을 "theirs"로 풀면 시크릿이 main에도 들어온다. 이미 원격에 올라간 값이라 머지 여부와 별개로 노출된 상태다(저장소 공개 범위는 미확인).
3. **`Member.id`(UUID)와 `Content.user_id`(bigint)의 타입이 다르다.** 토큰·principal에도 id가 없고 username만 있다(`CustomUserDetailsService.java:50-54`, `JwtTokenProvider.java:106-111`). 연결 방식(Content.userId 타입 변경, 별도 Long id 추가, username으로 조회 등)에 따라 기존 `content.user_id = 1` 행의 이관 방법이 달라진다. 결정 필요.
4. **미인증 시 응답과 프론트 영향.** security 쪽 체인은 `.cors()`가 없고 모든 경로가 `authenticated()`라서, CORS preflight와 프론트의 모든 호출이 막힐 수 있다. 프론트에는 로그인 화면·토큰 저장·`Authorization` 헤더 코드가 없다(`frontend/lib/api.ts` 포함 grep 0건). 백엔드 머지만으로는 앱이 동작하지 않는다.
5. **`JwtAuthenticationFilter.java:55`는 예외를 잡지 않는다.** 서명이 유효한 토큰의 사용자가 DB에서 삭제되면 `CustomUserDetailsService`가 `UsernameNotFoundException`을 던지고, 필터 단계에서 터져 500이 될 수 있다(추정).
6. **refresh 토큰을 access 토큰처럼 쓸 수 있다.** 종류 구분 claim이 없고 같은 키로 서명한다(`JwtTokenProvider.java:102-112`). 재발급 API는 없는데 7일짜리 토큰이 접근 권한으로 통한다(코드 기준).
7. **로그인 실패 메시지가 사용자 존재 여부를 알려 준다.** "존재하지 않는 사용자입니다."와 "비밀번호가 일치하지 않습니다."가 구분되고(`AuthService.java:74,79`), `GlobalExceptionHandler`가 `IllegalArgumentException`을 400으로 변환한다(`exception/GlobalExceptionHandler.java:22`). 아이디 열거가 가능하다.
8. **`StatisticsController.java:32`의 `month` NPE는 그대로다**(04번에서 이미 기록). `month`가 `required = false`인데 null 체크보다 먼저 비교한다. 인증과는 무관하지만 머지 후 확인할 때 걸릴 수 있다.
9. **`docs/*.md` 5개(약 2200줄)가 security 브랜치에서 같이 들어온다.** main 이전 시점 기준으로 쓰였을 가능성이 있어 현재 코드(Redis 캐시, Specification 하이라이트 조회, 전역 CORS)와 안 맞을 수 있다(내용은 읽지 않음, **미확인**).
10. **security 쪽 커밋 작성자는 NIsik419이고 날짜가 2026-05-02**로 main 작업(2026-10)보다 훨씬 이전이다. 머지 후 이력/책임 구분이 필요하면 참고.
11. **캘린더 컨트롤러 주석의 대체 방식**(`@AuthenticationPrincipal`, `CalendarController.java:32`)은 지금 principal이 Spring `User`(username만)라서 그대로는 id를 얻을 수 없다. 3번과 같은 문제다.

---

## 미확인 항목 확인 절차 (은서가 직접)
1. **로컬 `application.yml` 복구:** 팀원 공유본이나 Supabase 대시보드(접속 정보)에서 다시 만든다. 복구 후 `./gradlew test`를 다시 돌리면 `DemoApplicationTests`, `HighlightRepositoryTest`가 실제 DB에 붙는지 로그의 `HikariPool ... url=jdbc:postgresql`로 확인할 수 있다.
2. **기존 행의 user_id 상태:** 04번 문서의 SQL ①② (`SELECT COUNT(*) FILTER (WHERE user_id IS NULL) ...`)를 Supabase SQL Editor에서 실행한다.
3. **미인증 응답 코드:** 머지 후 토큰 없이 `curl -i http://localhost:8080/api/highlights`를 호출해 401인지 403인지 본다(9번 추정 확인).
4. **TMDB 커밋:** feat/security에 TMDB 관련 커밋이 있다고 보셨다면, 커밋 해시나 브랜치 이름을 알려 주세요(4번 미확인).
5. **저장소 공개 범위:** GitHub에서 `Eunseo-ee/book-log`가 public인지 확인한다(발견 사항 2번의 시급도 판단).

## 메모
- 이번 세션은 조사만 했다. 코드·설정·테스트·기존 task 파일은 수정하지 않았고, 커밋·push도 하지 않았다. 새로 만든 파일은 이 문서 하나다(scratchpad의 임시 복사본 제외).
- 머지 시도는 `git merge --no-commit --no-ff origin/feat/security` → 충돌 확인 → `git merge --abort` 순서로 한 번만 했다. 시작 전과 abort 후 모두 `git status`가 `nothing to commit, working tree clean`이었다(gitignore 대상 파일은 `git status`에 나타나지 않으므로 발견 사항 1번의 삭제는 이 출력에 보이지 않는다).
- `./gradlew test`는 `demo/`에서 실행했고, 결과는 `build/`(gitignore)에만 쓰인다.
- CLAUDE.md의 완료 정리 형식(선택지, 선택 이유)과 복습 질문은 해결 방안을 정하는 구현 세션에서 작성한다.
