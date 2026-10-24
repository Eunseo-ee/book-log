# 13. 회원 + 회원가입/로그인 (토큰 없이)

- 브랜치: `feat/auth-member` (시작 시 `git status`는 `.claude/tasks/12-security-merge-investigation.md` 하나만 untracked. 사용자가 예외로 허용했고, 이 파일은 수정하지 않았으며 커밋 대상에도 넣지 않는다)
- 참고: `.claude/decisions.md` 2026-10-24 항목 3개, `.claude/tasks/12-security-merge-investigation.md` (발견 사항 3·6·7), `.claude/tasks/08-cors-global.md`
- `origin/feat/security`는 읽지도 머지하지도 않았다. 로컬 `application.yml`, `application-api-key.yml`은 삭제·수정·이동·출력하지 않았다.

## 배경
로그인/JWT를 팀원 브랜치(`feat/security`)에서 가져오지 않고 새로 설계한다. 12번 조사에서 그쪽은 `Member.id`가 UUID라 `Content.userId`(Long)와 타입이 안 맞고, 로그인 실패 메시지가 아이디 존재 여부를 알려 주며(발견 사항 7), 시크릿이 평문으로 추적되고 있었다. 이번 task는 첫 단계로 "회원 + 회원가입/로그인(토큰 없이)"까지만 한다. JWT는 task 14.

## 목표
- `Member` 엔티티와 `members` 테이블
- `POST /api/auth/register`(201, id/username만), `POST /api/auth/login`(200, id/username만)
- BCrypt 해시 저장, 입력 검증, 중복·로그인 실패 에러 처리

## 범위
- 수정 허용: `domain/Member`·`Role`(신규), `repository/MemberRepository`(신규), `service/AuthService`(신규), `controller/AuthController`(신규), DTO 3개(신규), 예외 2개(신규), `config/SecurityConfig`(PasswordEncoder 빈 + TODO 주석만), `exception/GlobalExceptionHandler`(핸들러 2개 추가만), 테스트 2개(신규), `.claude/decisions.md`·`backlog.md`(추가만), 이 파일
- 범위 밖: JWT/인증 필터/jjwt, Content↔Member 연결·userId 변경(task 16~18), authenticated 전환(task 19), 프론트엔드, 기존 테스트 수정

## 설계
| 항목 | 내용 |
|---|---|
| Member | `id` Long IDENTITY, `username` unique·not null, `password` not null(BCrypt 해시), `email` not null, `role` enum(`ROLE_USER`, `ROLE_ADMIN`) STRING·not null·`@Builder.Default ROLE_USER`, `BaseTimeEntity` 상속, `@Table(name = "members")` |
| 검증 | 가입: username 4~20, password 8~64(상한 포함), email 형식·100자 이내. 로그인: 둘 다 NotBlank + 길이 상한(20/64)만 (하한을 두면 정책이 노출되고 의미도 없다) |
| 응답 | `MemberResponseDto(id, username)` 하나로 가입·로그인 공용. password 필드 자체가 없다 |
| DTO | `@Data` 대신 `@Getter`만 써서 `toString()`이 없다 → 로그에 비밀번호가 찍힐 경로를 만들지 않음 |
| SecurityConfig | `PasswordEncoder`(BCrypt) 빈만 추가. `.cors()`, `CorsConfigurationSource` 빈, `anyRequest().permitAll()`은 그대로. `/api/auth/**`는 따로 열지 않음(이미 전체 permitAll). `// TODO: task 19에서 authenticated 전환` |

## 에러 처리 선택
1. **아이디 중복: 409 (`DuplicateUsernameException` 신규)**
   - 선택지: (A) 기존 패턴대로 `IllegalStateException` → 400 (B) 전용 예외 → 409
   - 기존 `GlobalExceptionHandler`는 `IllegalStateException`을 "중복 데이터"로 400 처리한다(`:15-20`). 일관성만 보면 A가 쉽다.
   - B를 골랐다. 이유: 클라이언트가 "입력이 잘못됨(400)"과 "이미 있는 아이디(409)"를 상태 코드로 구분할 수 있다. `IllegalStateException`은 서비스 내부 상태 오류도 같이 400으로 삼키는 예외라 의미가 흐리다. 트레이드오프는 핸들러가 하나 늘고, "중복은 400"이라는 기존 관례(콘텐츠 중복)와 이 API만 달라진다는 점. 다른 중복 응답(ContentService)을 409로 바꾸는 것은 이번 범위 밖이라 건드리지 않았다.
   - 동시 가입 대비: `existsByUsername` 검사를 통과해도 unique 제약에서 걸릴 수 있어 `saveAndFlush`로 즉시 반영하고 `DataIntegrityViolationException`을 같은 중복 예외로 바꾼다(그냥 `save`면 flush가 커밋 시점이라 try/catch에 안 걸림).
2. **로그인 실패: 하나의 예외·하나의 메시지·같은 상태 코드 (`InvalidCredentialsException` → 401)**
   - "아이디 없음"과 "비밀번호 틀림"을 구분하면 공격자가 아이디 존재 여부를 알아낼 수 있다(아이디 열거). 12번 발견 사항 7에서 security 브랜치가 정확히 이 문제였다. 이유는 decisions.md에도 기록.
   - 응답 시간으로도 구분되지 않게, 없는 아이디도 미리 만든 더미 해시와 `matches`를 한 번 수행한다(BCrypt는 일부러 느려서, 없는 아이디만 즉시 응답하면 시간 차이가 생긴다).
   - 상태 코드 401은 비즈니스 예외를 `GlobalExceptionHandler`에서 변환한 것이다. 시큐리티 필터 체인의 401/403 처리(진입점·접근 거부 핸들러)는 task 19 범위라 건드리지 않았다. 이 해석이 달라도 핸들러 한 줄 수정이다.
3. 검증 실패(`@Valid`)는 기존 핸들러(필드명→메시지 Map, 400)를 그대로 쓴다. 응답에 입력값은 들어가지 않는다.

## 테스트
- `AuthServiceTest` (Mockito, DB 없음, 5개): 가입 성공(저장된 값이 평문과 다르고 `$2`로 시작하며 `matches`로 검증됨, role=ROLE_USER) / 중복 아이디 거부(저장 안 함) / 동시 가입 제약 위반→중복 예외 / 로그인 성공 / 로그인 실패(없는 아이디·틀린 비밀번호가 같은 예외 타입, 같은 메시지, 비밀번호 미포함)
- `AuthControllerTest` (`@WebMvcTest` + `@Import(SecurityConfig)`, DB 없음, 7개): 가입 201에 password 필드 없음 / username 3자·21자 400 / password 7자·65자 400(응답에 입력 비밀번호 없음) / 이메일 형식 400 / 중복 409 / 로그인 성공 password 필드 없음 / 로그인 실패 원인이 달라도 401과 같은 메시지
- 기존 테스트는 하나도 수정하지 않았다(`HighlightRepositoryTest` 포함).

## 테스트 실행 결과와 DB 확인
- `./gradlew test` → **BUILD SUCCESSFUL, 98개 통과, 실패 0, 스킵 0** (기존 86 + 신규 12). 신규: `AuthServiceTest` 5, `AuthControllerTest` 7.
- **어느 DB로 도는지 (테스트 결과 XML의 로그 기준, 접속 URL/호스트는 출력하지 않음):**
  - `org.postgresql.jdbc.PgConnection`으로 연결한 풀이 2개, `Database version: 17.6`(PostgreSQL). 즉 `DemoApplicationTests`와 `HighlightRepositoryTest`는 **로컬 yml이 가리키는 실제 PostgreSQL(Supabase로 추정)에 붙는다.** CLAUDE.md의 "테스트는 H2" 규칙과 다르다(기존 backlog 항목과 같은 문제).
  - `jdbc:h2:mem`은 1개(`HighlightFilterRepositoryTest`). 이쪽만 임베디드 H2.
- **members 테이블이 실제 DB에 만들어졌을 가능성이 높다.** `DemoApplicationTests`가 전체 컨텍스트를 띄우며 `create table members`와 `alter table if exists members ...` 로그가 남았고, 이 테스트가 PostgreSQL에 붙는다. 정확한 반영 여부(Supabase 대시보드에서 `members` 테이블 존재 확인)는 **미확인**이다. 로컬 yml의 `ddl-auto` 값은 보지 않았다. 필요 없으면 직접 정리: `DROP TABLE members;`
- 마이그레이션 도구(Flyway 등)는 이 레포에 없다(`build.gradle` 기준). 운영 DB에 스키마를 어떻게 반영할지는 결정이 필요하다.

## DoD
- [x] Member/Role/MemberRepository, `members` 테이블명
- [x] PasswordEncoder 빈 추가, 기존 CORS·permitAll 유지, TODO 주석
- [x] `POST /api/auth/register` 201 + id/username만, `POST /api/auth/login` 200 + id/username만
- [x] `@Valid` 검증(4~20 / 8~64 / email)
- [x] 중복 409, 로그인 실패 단일 메시지·단일 상태 코드, decisions.md 기록
- [x] 신규 테스트 12개 + `./gradlew test` 전체 통과
- [x] JWT·Content 연결·프론트 변경 없음, 커밋·push 없음

## 변경 파일
- 수정: `config/SecurityConfig.java`, `exception/GlobalExceptionHandler.java`, `.claude/decisions.md`(추가), `.claude/backlog.md`(추가)
- 신규(main): `domain/Member.java`, `domain/Role.java`, `repository/MemberRepository.java`, `service/AuthService.java`, `controller/AuthController.java`, `dto/request/RegisterRequestDto.java`, `dto/request/LoginRequestDto.java`, `dto/response/MemberResponseDto.java`, `exception/DuplicateUsernameException.java`, `exception/InvalidCredentialsException.java`
- 신규(test): `service/AuthServiceTest.java`, `controller/AuthControllerTest.java`
- 신규(문서): 이 파일

---

## 정리
### 요약
회원 엔티티와 가입/로그인 API를 토큰 없이 만들었다. 비밀번호는 BCrypt 해시로만 저장하고 응답·예외·DTO `toString`에 평문이 나오지 않는다. 중복 아이디는 409, 로그인 실패는 원인과 무관하게 401과 같은 메시지다. SecurityConfig는 `PasswordEncoder` 빈만 추가했고 CORS와 `permitAll`은 그대로다.

### 1. 문제 상황
security 브랜치의 인증은 `Member.id`가 UUID라 기존 `Content.userId`(Long)와 맞지 않았고, 로그인 실패 메시지가 아이디 존재 여부를 알려 주었다. 머지 대신 새로 설계하기로 했다.

### 2. 고려한 선택지
- 중복 아이디: 기존 `IllegalStateException`(400) 재사용 / 전용 예외 + 409
- 로그인 실패: 원인별 메시지 / 단일 메시지 (+ 더미 해시 비교로 시간차 제거)
- id 타입: UUID / Long

### 3. 선택 이유
- 409 전용 예외: 클라이언트가 400(입력 오류)과 409(이미 존재)를 구분하고, 범용 예외가 의미를 흐리지 않는다. 대신 기존 중복=400 관례와 달라진다.
- 단일 메시지: 아이디 열거 방지. 비용은 사용자에게 "어느 쪽이 틀렸는지" 알려 주지 못하는 불편.
- Long: `Content.userId`와 맞고, id가 외부에 노출되지 않아 추측 가능성이 큰 위험이 아니다(아이디 열거는 로그인 메시지에서 막는다).

### 4. 막혔던 부분과 해결
- 첫 파일 생성 명령이 셸 따옴표 문제로 통째로 실패해 아무것도 만들어지지 않았다 → 파일 쓰기 도구로 하나씩 생성.
- `save` + try/catch로는 동시 가입 시 unique 위반이 커밋 시점에 터져 못 잡는다 → `saveAndFlush`로 해결.

## 발견 사항 (범위 밖, 기록만)
1. **테스트가 실제 PostgreSQL에 붙고 테이블을 만든다.** `HighlightRepositoryTest.java:31`(`Replace.NONE`)과 `DemoApplicationTests`가 해당. CLAUDE.md의 "테스트는 H2" 규칙과 불일치. 이번 실행에서 `members` 생성 로그 확인. 테스트 전용 DB/프로파일 분리 필요.
2. **비밀번호 상한 64자 vs BCrypt 72바이트.** Spring Security 6.4의 BCrypt는 72바이트를 넘으면 뒤를 잘라 해시한다(추정, 버전별 동작 차이는 미확인). 한글은 한 글자가 3바이트라 64자 이내여도 초과할 수 있다. `RegisterRequestDto.java`의 `@Size(max = 64)`는 문자 수 기준이다.
3. **로그인 시도 횟수 제한이 없다.** 401이 같아도 무차별 대입은 막지 못한다. (`AuthController.java` login)
4. **이메일 중복 허용, 아이디 대소문자 구분.** `Member.java:25` 이메일에 unique 없음, `Alice`와 `alice`는 다른 아이디. 정책 결정 필요.
5. **검증 실패 응답 형식 불일치.** `GlobalExceptionHandler.java:54`는 `Map<필드, 메시지>`, 나머지는 `ErrorResponse(status, message)`. 프론트가 두 형태를 다뤄야 한다.
6. **운영 스키마 반영 수단이 없다.** Flyway/Liquibase 없음. 운영(Supabase)에 `members` 테이블을 어떻게 만들지 미결정.
7. **로그인 성공 응답에 인증 상태가 없다.** 지금은 id/username만 주고 서버는 아무것도 기억하지 않는다(토큰은 task 14). 이 상태의 `/login`은 "자격 증명이 맞는지 확인"하는 역할뿐이다.
8. **`GlobalExceptionHandler.java:68`의 `e.printStackTrace()`.** 예기치 못한 예외 스택트레이스에 요청 값이 섞여 들어갈 수 있는지는 미확인. 비밀번호가 예외 메시지에 들어가지 않도록 이번 코드는 설계했지만, 프레임워크 예외(JSON 파싱 실패 등)의 메시지는 확인하지 않았다.

## 다음 task 제안 (task 14: JWT 발급/검증)
- jjwt 의존성 추가, `JwtTokenProvider`(시크릿은 환경 변수/프로퍼티 주입, 레포에 값 금지), `/login` 응답에 access 토큰 추가
- 토큰 claim에 `sub`=Member.id(Long)를 넣고 username은 보조로. 12번 발견 사항 3·11(principal에 id가 없던 문제)을 피한다
- access와 refresh를 구분하는 claim(12번 발견 사항 6), 만료 시간은 프로퍼티화
- 인증 필터는 만들되 `anyRequest().permitAll()`은 유지하고 토큰이 있을 때만 principal을 채운다(authenticated 전환은 task 19)
- 사전 결정: 발견 사항 1(테스트 DB 분리), 6(스키마 반영 수단)

## 복습 질문
(CLAUDE.md 규칙: 토글로 제공, 토글 안에 답)

<details><summary>Q1. BCrypt가 단순 해시(SHA-256)보다 비밀번호 저장에 적합한 이유는?</summary>

답: SHA-256은 빠르게 계산되도록 설계돼 있어 유출된 해시를 GPU로 초당 수십억 번 대입해 깨기 쉽다. BCrypt는 (1) 일부러 느리고 cost 값으로 느린 정도를 조절할 수 있으며 (2) 해시마다 무작위 salt를 내장해 같은 비밀번호도 다른 해시가 되어 레인보우 테이블이 무력해진다. 이 코드에서는 `AuthServiceTest`가 저장된 값이 `$2`로 시작하고 `matches`로만 검증되는 것을 확인한다.
</details>

<details><summary>Q2. 로그인 실패 메시지를 하나로 합친 이유는? 메시지만 합치면 충분한가?</summary>

답: "존재하지 않는 아이디"와 "비밀번호 불일치"를 구분하면 공격자가 아이디 존재 여부를 알아내(아이디 열거) 존재하는 아이디에만 비밀번호 대입을 집중할 수 있다. 메시지뿐 아니라 상태 코드가 같아야 하고(`InvalidCredentialsException` 하나 → 401), 응답 시간도 같아야 한다. 그래서 `AuthService.login`은 없는 아이디도 더미 해시와 `matches`를 한 번 수행한다. 12번 조사에서 security 브랜치는 메시지가 구분돼 이 문제가 있었다.
</details>

<details><summary>Q3. 기존 SecurityConfig의 permitAll/CORS 설정과 새로 추가한 PasswordEncoder 빈은 어떻게 같이 있나?</summary>

답: 서로 독립이다. `SecurityFilterChain` 빈은 "누가 어떤 요청을 할 수 있나"(CORS 적용, CSRF 끔, `anyRequest().permitAll()`)를 정하고, `PasswordEncoder` 빈은 "비밀번호를 어떻게 해시/비교하나"만 제공해 `AuthService`가 주입받아 쓴다. 필터 체인을 건드리지 않았으므로 `/api/auth/**`는 이미 permitAll로 열려 있고 CORS 동작(8번 task의 `CorsConfigTest`)도 그대로다. 나중에 task 19에서 `permitAll`을 `authenticated`로 바꿔도 `PasswordEncoder` 빈은 영향이 없고, 그때 `/api/auth/**`만 다시 열면 된다.
</details>
